package se.sundsvall.dept44.configuration.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static se.sundsvall.dept44.configuration.Constants.DEFAULT_TOKEN_CONNECT_TIMEOUT_IN_SECONDS;
import static se.sundsvall.dept44.configuration.Constants.DEFAULT_TOKEN_READ_TIMEOUT_IN_SECONDS;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;

public class OAuth2RequestInterceptor implements RequestInterceptor {

	private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken(
		"anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private final OAuth2AuthorizedClientManager authorizedClientManager;
	private final ClientRegistration clientRegistration;
	private final InMemoryOAuth2AuthorizedClientService oAuth2AuthorizedClientService;

	public OAuth2RequestInterceptor(final ClientRegistration clientRegistration, Set<String> extraScopes) {
		this(clientRegistration, extraScopes, Duration.ofSeconds(DEFAULT_TOKEN_CONNECT_TIMEOUT_IN_SECONDS), Duration.ofSeconds(DEFAULT_TOKEN_READ_TIMEOUT_IN_SECONDS));
	}

	/**
	 * Creates an interceptor with explicit timeouts for the calls to the token endpoint.
	 * <p>
	 * The token is fetched while a lock is held, so a token endpoint that never responds would otherwise block every
	 * outbound request for this client indefinitely. Note that the timeouts configured on the Feign client itself do not
	 * apply to the token fetch.
	 *
	 * @param clientRegistration containing authorization information for the client
	 * @param extraScopes        a set of extra scopes (may be null or empty)
	 * @param connectTimeout     connect timeout for calls to the token endpoint
	 * @param readTimeout        read timeout for calls to the token endpoint
	 */
	public OAuth2RequestInterceptor(final ClientRegistration clientRegistration, final Set<String> extraScopes, final Duration connectTimeout, final Duration readTimeout) {
		Assert.notNull(clientRegistration, "clientRegistration cannot be null");
		Assert.notNull(connectTimeout, "connectTimeout cannot be null");
		Assert.notNull(readTimeout, "readTimeout cannot be null");

		Set<String> scope = getScopeSet(clientRegistration);
		ofNullable(extraScopes).ifPresent(scope::addAll);
		this.clientRegistration = ClientRegistration.withClientRegistration(clientRegistration).scope(scope).build();

		var clientRegistrations = new InMemoryClientRegistrationRepository(this.clientRegistration);
		oAuth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrations);

		this.authorizedClientManager = createAuthorizedClientManager(clientRegistrations, oAuth2AuthorizedClientService, connectTimeout, readTimeout);
	}

	private Set<String> getScopeSet(final ClientRegistration clientRegistration) {
		// When adding a scope to the clientRegistration it produces an "UnmodifiableSet", work around it.
		return ofNullable(clientRegistration.getScopes())
			.map(HashSet::new)
			.orElseGet(HashSet::new);
	}

	private AuthorizedClientServiceOAuth2AuthorizedClientManager createAuthorizedClientManager(
		InMemoryClientRegistrationRepository clientRegistrationRepository,
		InMemoryOAuth2AuthorizedClientService clientService,
		final Duration connectTimeout,
		final Duration readTimeout) {

		var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
		var clientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials(clientCredentials -> clientCredentials.accessTokenResponseClient(createTokenResponseClient(connectTimeout, readTimeout)))
			.build();

		manager.setAuthorizedClientProvider(clientProvider);
		return manager;
	}

	/**
	 * Creates a token response client with explicit timeouts. Without them, the default request factory has no read
	 * timeout, and a hung token endpoint would block indefinitely while the token fetch lock is held.
	 */
	private RestClientClientCredentialsTokenResponseClient createTokenResponseClient(final Duration connectTimeout, final Duration readTimeout) {
		final var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.build());
		requestFactory.setReadTimeout(readTimeout);

		// The message converters and status handler mirror the defaults used by
		// RestClientClientCredentialsTokenResponseClient, which are lost when replacing its RestClient.
		final var restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.messageConverters(converters -> {
				converters.clear();
				converters.add(new FormHttpMessageConverter());
				converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
			})
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
			.build();

		final var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
		tokenResponseClient.setRestClient(restClient);
		return tokenResponseClient;
	}

	@Override
	public void apply(final RequestTemplate requestTemplate) {
		if (isNull(authorizedClientManager)) {
			return;
		}

		var request = OAuth2AuthorizeRequest
			.withClientRegistrationId(clientRegistration.getRegistrationId())
			.principal(ANONYMOUS_AUTHENTICATION)
			.build();

		OAuth2AuthorizedClient authorizedClient;
		synchronized (this) {
			authorizedClient = authorizedClientManager.authorize(request);
		}
		var accessToken = requireNonNull(authorizedClient, "authorizedClient cannot be null").getAccessToken();
		requestTemplate.removeHeader(AUTHORIZATION);
		requestTemplate.header(AUTHORIZATION, String.format("Bearer %s", accessToken.getTokenValue()));
	}

	/**
	 * Evicts the cached token, but only if it is still the exact token that caused the failing request.
	 * <p>
	 * When several threads share this interceptor (e.g. via {@code FeignMultiCustomizer}) and a token is rejected by
	 * WSO2, every in-flight request carrying that token triggers a retry and calls this method. An unconditional
	 * eviction would make each queued thread remove whatever token is currently cached - including a fresh, valid token
	 * that another thread just fetched - causing a token-refresh storm and requests failing after their single retry.
	 * By comparing the cached token against the one that actually failed, only the first thread evicts it; the rest
	 * become no-ops and their retry reuses the freshly fetched token.
	 *
	 * @param failedAuthorizationHeader the {@code Authorization} header value (e.g. {@code "Bearer <token>"}) of the
	 *                                  request that failed
	 */
	public void removeToken(final String failedAuthorizationHeader) {
		synchronized (this) {
			final var registrationId = clientRegistration.getRegistrationId();
			final OAuth2AuthorizedClient currentClient = oAuth2AuthorizedClientService.loadAuthorizedClient(registrationId, ANONYMOUS_AUTHENTICATION.getName());
			if (currentClient == null) {
				// Already evicted (and possibly refreshed) by another thread - nothing to do.
				return;
			}

			final var currentAuthorizationHeader = String.format("Bearer %s", currentClient.getAccessToken().getTokenValue());
			if (currentAuthorizationHeader.equals(failedAuthorizationHeader)) {
				oAuth2AuthorizedClientService.removeAuthorizedClient(registrationId, ANONYMOUS_AUTHENTICATION.getName());
			}
		}
	}
}
