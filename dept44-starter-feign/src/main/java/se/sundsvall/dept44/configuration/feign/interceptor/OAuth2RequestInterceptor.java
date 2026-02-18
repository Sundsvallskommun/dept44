package se.sundsvall.dept44.configuration.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.Assert;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;

public class OAuth2RequestInterceptor implements RequestInterceptor {

	private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken(
		"anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private final OAuth2AuthorizedClientManager authorizedClientManager;
	private final ClientRegistration clientRegistration;
	private final InMemoryOAuth2AuthorizedClientService oAuth2AuthorizedClientService;

	public OAuth2RequestInterceptor(final ClientRegistration clientRegistration, Set<String> extraScopes) {
		Assert.notNull(clientRegistration, "clientRegistration cannot be null");

		Set<String> scope = getScopeSet(clientRegistration);
		ofNullable(extraScopes).ifPresent(scope::addAll);
		this.clientRegistration = ClientRegistration.withClientRegistration(clientRegistration).scope(scope).build();

		var clientRegistrations = new InMemoryClientRegistrationRepository(this.clientRegistration);
		oAuth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrations);

		this.authorizedClientManager = createAuthorizedClientManager(clientRegistrations, oAuth2AuthorizedClientService);
	}

	private Set<String> getScopeSet(final ClientRegistration clientRegistration) {
		// When adding a scope to the clientRegistration it produces an "UnmodifiableSet", work around it.
		return ofNullable(clientRegistration.getScopes())
			.map(HashSet::new)
			.orElseGet(HashSet::new);
	}

	private AuthorizedClientServiceOAuth2AuthorizedClientManager createAuthorizedClientManager(
		InMemoryClientRegistrationRepository clientRegistrationRepository,
		InMemoryOAuth2AuthorizedClientService clientService) {

		var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
		var clientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

		manager.setAuthorizedClientProvider(clientProvider);
		return manager;
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

	public void removeToken() {
		synchronized (this) {
			oAuth2AuthorizedClientService.removeAuthorizedClient(clientRegistration.getRegistrationId(), ANONYMOUS_AUTHENTICATION.getName());
		}
	}
}
