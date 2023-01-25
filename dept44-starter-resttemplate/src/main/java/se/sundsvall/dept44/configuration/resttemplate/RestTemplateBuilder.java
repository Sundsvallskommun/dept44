package se.sundsvall.dept44.configuration.resttemplate;

import static java.util.Objects.nonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

import java.time.Duration;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.client.RestTemplate;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient.LogbookHttpRequestInterceptor;
import org.zalando.logbook.httpclient.LogbookHttpResponseInterceptor;

import se.sundsvall.dept44.configuration.Constants;
import se.sundsvall.dept44.configuration.resttemplate.interceptor.OAuth2ClientCredentialsRestTemplateInterceptor;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.BasicAuthentication;

/**
 * Deprecated module. Use another web client (for example Feign) instead.
 * 
 * @deprecated
 */
@Deprecated(since = "2022-10-10", forRemoval = true)
public class RestTemplateBuilder {

	private String baseUrl;

	private Duration connectTimeout = Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS);
	private Duration readTimeout = Duration.ofSeconds(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS);

	private Logbook logbook;
	private ClientRegistration oAuth2ClientRegistration;
	private BasicAuthentication basicAuthentication;

	/**
	 * Sets the base URL.
	 *
	 * @param baseUrl the base URL
	 * @return this builder
	 */
	public RestTemplateBuilder withBaseUrl(final String baseUrl) {
		this.baseUrl = requireNotBlank(baseUrl, "baseUrl may not be blank");
		return this;
	}

	/**
	 * Sets the connect timeout (defaults to 10 seconds).
	 *
	 * @param connectTimeout the connect timeout
	 * @return this builder
	 */
	public RestTemplateBuilder withConnectTimeout(final Duration connectTimeout) {
		this.connectTimeout = requireNonNull(connectTimeout, "connectTimeout may not be null");
		return this;
	}

	/**
	 * Sets the read timeout (defaults to 60 seconds).
	 *
	 * @param readTimeout the read timeout
	 * @return this builder
	 */
	public RestTemplateBuilder withReadTimeout(final Duration readTimeout) {
		this.readTimeout = requireNonNull(readTimeout, "readTimeout may not be null");
		return this;
	}

	/**
	 * Sets the OAuth2 client details.
	 *
	 * @param oAuth2ClientRegistration the OAuth2 client details
	 * @return this builder
	 */
	public RestTemplateBuilder withOAuth2Client(final ClientRegistration oAuth2ClientRegistration) {
		this.oAuth2ClientRegistration = oAuth2ClientRegistration;
		return this;
	}

	/**
	 * Sets Basic authentication details.
	 *
	 * @param username the Basic authentication username
	 * @param password the Basic authentication password
	 * @return this builder
	 */
	public RestTemplateBuilder withBasicAuthentication(final String username, final String password) {
		basicAuthentication = new BasicAuthentication(username, password);
		return this;
	}

	/**
	 * Sets the Logbook instance to use for payload logging.
	 *
	 * @param logbook the Logbook instance
	 * @return this builder
	 */
	public RestTemplateBuilder withLogbook(final Logbook logbook) {
		this.logbook = logbook;
		return this;
	}

	/**
	 * Builds the RestTemplate.
	 *
	 * @return a RestTemplate
	 */
	public RestTemplate build() {
		if (nonNull(basicAuthentication) && nonNull(oAuth2ClientRegistration)) {
			throw new IllegalStateException("Basic Auth and OAuth2 cannot be used simultaneously");
		}

		var clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(createCloseableHttpClient());
		clientHttpRequestFactory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
		clientHttpRequestFactory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));

		var builder = new org.springframework.boot.web.client.RestTemplateBuilder()
			.rootUri(baseUrl)
			.requestFactory(() -> clientHttpRequestFactory)
			.setConnectTimeout(connectTimeout)
			.setReadTimeout(readTimeout)
			.interceptors((request, body, execution) -> {
				request.getHeaders().add(RequestId.HEADER_NAME, RequestId.get());

				return execution.execute(request, body);
			});

		if (nonNull(basicAuthentication)) {
			builder = builder.basicAuthentication(basicAuthentication.username(), basicAuthentication.password());
		}

		if (nonNull(oAuth2ClientRegistration)) {
			var clientRegistrations = new InMemoryClientRegistrationRepository(oAuth2ClientRegistration);
			var oAuth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrations);
			var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, oAuth2AuthorizedClientService);
			authorizedClientManager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials()
				.build());

			builder = builder.additionalInterceptors(
				new OAuth2ClientCredentialsRestTemplateInterceptor(authorizedClientManager, oAuth2ClientRegistration));
		}

		return builder.build();
	}

	private CloseableHttpClient createCloseableHttpClient() {
		var builder = HttpClientBuilder.create();

		if (nonNull(logbook)) {
			builder
				.addInterceptorFirst(new LogbookHttpRequestInterceptor(logbook))
				.addInterceptorLast(new LogbookHttpResponseInterceptor());
		}

		return builder.build();
	}
}
