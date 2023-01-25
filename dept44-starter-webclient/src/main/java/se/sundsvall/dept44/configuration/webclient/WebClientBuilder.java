package se.sundsvall.dept44.configuration.webclient;

import static java.util.Objects.nonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

import java.time.Duration;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import se.sundsvall.dept44.configuration.Constants;
import se.sundsvall.dept44.support.BasicAuthentication;

public class WebClientBuilder {

	private String baseUrl;

	private Duration connectTimeout = Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS);
	private Duration readTimeout = Duration.ofSeconds(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS);
	private Duration writeTimeout = Duration.ofSeconds(Constants.DEFAULT_WRITE_TIMEOUT_IN_SECONDS);

	private Logbook logbook;
	private ClientRegistration oAuth2ClientRegistration;
	private BasicAuthentication basicAuthentication;

	/**
	 * Sets the base URL.
	 *
	 * @param baseUrl the base URL
	 * @return this builder
	 */
	public WebClientBuilder withBaseUrl(final String baseUrl) {
		this.baseUrl = requireNotBlank(baseUrl, "baseUrl may not be blank.");
		return this;
	}

	/**
	 * Sets the connect timeout (defaults to 10 seconds).
	 *
	 * @param connectTimeout the connect timeout
	 * @return this builder
	 */
	public WebClientBuilder withConnectTimeout(final Duration connectTimeout) {
		this.connectTimeout = requireNonNull(connectTimeout, "connectTimeout may not be null.");
		return this;
	}

	/**
	 * Sets the read timeout (defaults to 60 seconds).
	 *
	 * @param readTimeout the read timeout
	 * @return this builder
	 */
	public WebClientBuilder withReadTimeout(final Duration readTimeout) {
		this.readTimeout = requireNonNull(readTimeout, "readTimeout may not be null.");
		return this;
	}

	/**
	 * Sets the write timeout (defaults to 60 seconds).
	 *
	 * @param writeTimeout the write timeout
	 * @return this builder
	 */
	public WebClientBuilder withWriteTimeout(final Duration writeTimeout) {
		this.writeTimeout = requireNonNull(writeTimeout, "writeTimeout may not be null.");
		return this;
	}

	/**
	 * Sets the OAuth2 client details.
	 *
	 * @param oAuth2ClientRegistration the OAuth2 client details
	 * @return this builder
	 */
	public WebClientBuilder withOAuth2Client(final ClientRegistration oAuth2ClientRegistration) {
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
	public WebClientBuilder withBasicAuthentication(final String username, final String password) {
		basicAuthentication = new BasicAuthentication(username, password);
		return this;
	}

	/**
	 * Sets the Logbook instance to use for payload logging.
	 *
	 * @param logbook the Logbook instance
	 * @return this builder
	 */
	public WebClientBuilder withLogbook(final Logbook logbook) {
		this.logbook = logbook;
		return this;
	}

	/**
	 * Builds the WebClient.
	 *
	 * @return a WebClient
	 */
	public WebClient build() {
		if (nonNull(basicAuthentication) && nonNull(oAuth2ClientRegistration)) {
			throw new IllegalStateException("Basic Auth and OAuth2 cannot be used simultaneously");
		}

		var builder = WebClient.builder()
			.baseUrl(baseUrl)
			.filter(new RequestIdExchangeFilterFunction());

		if (nonNull(basicAuthentication)) {
			builder = builder.defaultHeaders(headers -> headers.setBasicAuth(basicAuthentication.username(), basicAuthentication.password()));
		}

		if (nonNull(oAuth2ClientRegistration)) {
			builder = builder.filter(createOAuth2Filter());
		}

		builder = builder.clientConnector(createClientConnector());
		return builder.build();
	}
	
	private ServerOAuth2AuthorizedClientExchangeFilterFunction createOAuth2Filter() {
		var clientRegistrations = new InMemoryReactiveClientRegistrationRepository(oAuth2ClientRegistration);
		var clientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);
		var oAuth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
			new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations, clientService));
		
		oAuth2Filter.setDefaultClientRegistrationId(oAuth2ClientRegistration.getRegistrationId());
		
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
			}
		));
	}
}
