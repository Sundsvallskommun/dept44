package se.sundsvall.dept44.configuration.webclient;

import static java.util.Optional.ofNullable;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import se.sundsvall.dept44.configuration.Constants;

public class WebClientBuilder {

	@FunctionalInterface
	public interface Customizer {
		void customize(WebClient.Builder builder);
	}

	private final List<Customizer> customizers;
	private String baseUrl;

	private Duration connectTimeout = Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS);
	private Duration readTimeout = Duration.ofSeconds(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS);
	private Duration writeTimeout = Duration.ofSeconds(Constants.DEFAULT_WRITE_TIMEOUT_IN_SECONDS);

	private Logbook logbook;

	public WebClientBuilder() {
		customizers = new ArrayList<>();
		customizers.add(builder -> builder.filter(new RequestIdExchangeFilterFunction()));
	}

	/**
	 * Sets the base URL.
	 *
	 * @param  baseUrl the base URL
	 * @return         this builder
	 */
	public WebClientBuilder withBaseUrl(final String baseUrl) {
		this.baseUrl = requireNotBlank(baseUrl, "baseUrl cannot be null or blank");
		return this;
	}

	/**
	 * Sets Basic authentication details.
	 *
	 * @param  username the Basic authentication username
	 * @param  password the Basic authentication password
	 * @return          this builder
	 */
	public WebClientBuilder withBasicAuthentication(final String username, final String password) {
		requireNotBlank(username, "username cannot be null or blank");
		requireNotBlank(password, "password cannot be null or blank");

		return withCustomizer(builder -> builder.defaultHeaders(headers -> headers.setBasicAuth(username, password)));
	}

	/**
	 * Sets the OAuth2 client registration.
	 *
	 * @param  clientRegistration the OAuth2 client registration
	 * @return                    this builder
	 */
	public WebClientBuilder withOAuth2ClientRegistration(final ClientRegistration clientRegistration) {
		return withOAuth2ClientRegistration(clientRegistration, Set.of());
	}

	/**
	 * Sets the OAuth2 client registration.
	 *
	 * @param  clientRegistration the OAuth2 client registration
	 * @param  extraScopes        extra scopes for the OAuth2 client registration
	 * @return                    this builder
	 */
	public WebClientBuilder withOAuth2ClientRegistration(final ClientRegistration clientRegistration,
		final Set<String> extraScopes) {
		requireNonNull(clientRegistration, "client registration cannot be null");

		final var scopes = getScopes(clientRegistration);
		ofNullable(extraScopes).ifPresent(scopes::addAll);

		final var clientRegistrationWithScopes = ClientRegistration.withClientRegistration(clientRegistration)
			.scope(scopes)
			.build();

		return withCustomizer(builder -> builder.filter(createOAuth2Filter(clientRegistrationWithScopes)));
	}

	private Set<String> getScopes(final ClientRegistration clientRegistration) {
		// When adding a scope to the clientRegistration it produces an "UnmodifiableSet", work around it.
		return ofNullable(clientRegistration.getScopes())
			.map(HashSet::new)
			.orElseGet(HashSet::new);
	}

	/***
	 * Adds a default header.
	 *
	 * @param  name   the header (name)
	 * @param  values the header values
	 * @return        this builder
	 */
	public WebClientBuilder withDefaultHeader(final String name, final String... values) {
		requireNotBlank(name, "name cannot be null or blank");
		requireNonNull(values, "values cannot be null");

		return withCustomizer(builder -> builder.defaultHeader(name, values));
	}

	/**
	 * Adds a filter (function).
	 *
	 * @param  filter the filter (function)
	 * @return        this builder
	 */
	public WebClientBuilder withFilter(final ExchangeFilterFunction filter) {
		requireNonNull(filter, "filter cannot be null");

		return withCustomizer(builder -> builder.filter(filter));
	}

	/**
	 * Adds a default HTTP status handler.
	 *
	 * @param  statusPredicate a predicate to match HTTP status codes with
	 * @param  handlerFunction a function to map the response to an error signal
	 * @return                 this builder
	 */
	public WebClientBuilder withStatusHandler(final Predicate<HttpStatusCode> statusPredicate,
		final Function<ClientResponse, Mono<? extends Throwable>> handlerFunction) {
		requireNonNull(statusPredicate, "status predicate cannot be null");
		requireNonNull(handlerFunction, "handler function cannot be null");

		return withCustomizer(builder -> builder.defaultStatusHandler(statusPredicate, handlerFunction));
	}

	/**
	 * Adds a customizer.
	 *
	 * @param  customizer the customizer
	 * @return            this builder
	 */
	public WebClientBuilder withCustomizer(final Customizer customizer) {
		requireNonNull(customizer, "customizer cannot be null");

		customizers.add(customizer);
		return this;
	}

	/**
	 * Sets the Logbook instance to use for payload logging.
	 *
	 * @param  logbook the Logbook instance
	 * @return         this builder
	 */
	public WebClientBuilder withLogbook(final Logbook logbook) {
		this.logbook = logbook;
		return this;
	}

	/**
	 * Sets the connect timeout (defaults to 10 seconds).
	 *
	 * @param  connectTimeout the connect timeout
	 * @return                this builder
	 */
	public WebClientBuilder withConnectTimeout(final Duration connectTimeout) {
		this.connectTimeout = requireNonNull(connectTimeout, "connectTimeout may not be null.");
		return this;
	}

	/**
	 * Sets the read timeout (defaults to 60 seconds).
	 *
	 * @param  readTimeout the read timeout
	 * @return             this builder
	 */
	public WebClientBuilder withReadTimeout(final Duration readTimeout) {
		this.readTimeout = requireNonNull(readTimeout, "readTimeout may not be null.");
		return this;
	}

	/**
	 * Sets the write timeout (defaults to 60 seconds).
	 *
	 * @param  writeTimeout the write timeout
	 * @return              this builder
	 */
	public WebClientBuilder withWriteTimeout(final Duration writeTimeout) {
		this.writeTimeout = requireNonNull(writeTimeout, "writeTimeout may not be null.");
		return this;
	}

	public WebClient build() {
		return configureBuilder().build();
	}

	public <T> T build(final Class<T> serviceType) {
		requireNonNull(serviceType, "serviceType cannot be null");

		final var clientAdapter = WebClientAdapter.create(configureBuilder().build());
		final var httpServiceProxyFactory = HttpServiceProxyFactory.builderFor(clientAdapter).build();

		return httpServiceProxyFactory.createClient(serviceType);
	}

	private WebClient.Builder configureBuilder() {
		final var builder = WebClient.builder()
			.baseUrl(baseUrl)
			.clientConnector(createClientConnector());

		customizers.forEach(customizer -> customizer.customize(builder));

		return builder;
	}

	private ServerOAuth2AuthorizedClientExchangeFilterFunction createOAuth2Filter(final ClientRegistration clientRegistration) {
		final var clientRegistrations = new InMemoryReactiveClientRegistrationRepository(clientRegistration);
		final var clientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);
		final var oAuth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
			new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations, clientService));
		oAuth2Filter.setDefaultClientRegistrationId(clientRegistration.getRegistrationId());

		return oAuth2Filter;
	}

	private ReactorClientHttpConnector createClientConnector() {
		return new ReactorClientHttpConnector(HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
			.doOnConnected(connection -> {
				connection
					.addHandlerLast(new ReadTimeoutHandler(Math.toIntExact(readTimeout.toSeconds())))
					.addHandlerLast(new WriteTimeoutHandler(Math.toIntExact(writeTimeout.toSeconds())));

				if (logbook != null) {
					connection.addHandlerLast(new LogbookClientHandler(logbook));
				}
			}));
	}
}
