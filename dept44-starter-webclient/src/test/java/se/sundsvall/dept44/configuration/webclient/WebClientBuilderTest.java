package se.sundsvall.dept44.configuration.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.zalando.logbook.Logbook;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WebClientBuilderTest {

	private static final String BASE_URL = "baseUrl";
	private static final String USER_NAME = "userName";
	private static final String PASSWORD = "password";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(54);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(55);
	private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(56);

	private static final Logbook LOGBOOK_MOCK = mock(Logbook.class);
	private static final ClientRegistration CLIENT_REGISTRATION_MOCK = mock(ClientRegistration.class);

	@Test
	void testNullFields() {
		assertThat(createBuilder(false))
			.hasAllNullFieldsOrPropertiesExcept("connectTimeout", "readTimeout", "writeTimeout", "customizers")
			.hasFieldOrPropertyWithValue("connectTimeout", Duration.ofSeconds(10))
			.hasFieldOrPropertyWithValue("readTimeout", Duration.ofSeconds(30))
			.hasFieldOrPropertyWithValue("writeTimeout", Duration.ofSeconds(30))
			.extracting("customizers").asInstanceOf(LIST).isNotNull().hasSize(1);
	}

	@Test
	void testCreatePatterns() {
		final var builder = createBuilder(true);

		assertThat(builder)
			.hasFieldOrPropertyWithValue("baseUrl", BASE_URL)
			.hasFieldOrPropertyWithValue("connectTimeout", CONNECT_TIMEOUT)
			.hasFieldOrPropertyWithValue("readTimeout", READ_TIMEOUT)
			.hasFieldOrPropertyWithValue("writeTimeout", WRITE_TIMEOUT)
			.hasFieldOrPropertyWithValue("logbook", LOGBOOK_MOCK)
			.extracting("customizers").asInstanceOf(LIST).isNotNull().hasSize(3);
	}

	@Test
	void testValueRestrictions() {
		final var builder = createBuilder(false);

		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(null))).hasMessage("baseUrl cannot be null or blank");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(""))).hasMessage("baseUrl cannot be null or blank");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(" "))).hasMessage("baseUrl cannot be null or blank");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withConnectTimeout(null))).hasMessage("connectTimeout may not be null.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withReadTimeout(null))).hasMessage("readTimeout may not be null.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withWriteTimeout(null))).hasMessage("writeTimeout may not be null.");
	}

	@Test
	void testBuildFromMinimumValues() {
		final var webClient = createBuilder(false).withBaseUrl(BASE_URL).build();

		assertTimeoutSetting(webClient, 10000);
		assertThat(webClient).extracting("defaultHeaders").isNull();
		assertThat(webClient).extracting("builder").extracting("baseUrl").asString().isEqualTo(BASE_URL);
		assertThat(webClient).extracting("builder").extracting("filters").asList()
			.hasSize(1)
			.hasOnlyElementsOfType(RequestIdExchangeFilterFunction.class);
	}

	@Test
	void testBuildFromCustomValuesWithBasicAuth() {
		final var webClient = createBuilder(true, true, false).build();

		assertTimeoutSetting(webClient, 54000);
		assertThat(webClient).extracting("defaultHeaders").asString().isEqualTo("[Authorization:\"Basic dXNlck5hbWU6cGFzc3dvcmQ=\"]");
		assertThat(webClient).extracting("builder").extracting("baseUrl").asString().isEqualTo(BASE_URL);
		assertThat(webClient).extracting("builder").extracting("filters").asList()
			.hasSize(1)
			.hasOnlyElementsOfType(RequestIdExchangeFilterFunction.class);
	}

	@Test
	void testBuildFromCustomValuesWithOAuth2() {
		when(CLIENT_REGISTRATION_MOCK.getRegistrationId()).thenReturn("registrationId");

		final var webClient = createBuilder(true, false, true).build();

		assertTimeoutSetting(webClient, 54000);
		assertThat(webClient).extracting("defaultHeaders").isNull();
		assertThat(webClient).extracting("builder").extracting("baseUrl").asString().isEqualTo(BASE_URL);
		assertThat(webClient).extracting("builder").extracting("filters").asList()
			.hasSize(2)
			.hasAtLeastOneElementOfType(RequestIdExchangeFilterFunction.class)
			.hasAtLeastOneElementOfType(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
	}

	@Test
	void testBuildWithDefaultHeader() {
		final var webClient = createBuilder(false)
			.withDefaultHeader("someHeader", "someValue")
			.build();

		assertThat(webClient).extracting("defaultHeaders").asString().isEqualTo("[someHeader:\"someValue\"]");
	}

	@Test
	void testBuildWithStatusHandler() {
		var webClient = createBuilder(false).build();

		assertThat(webClient).extracting("defaultStatusHandlers").asList().isEmpty();

		webClient = createBuilder(false)
			.withStatusHandler(HttpStatusCode::isError, clientResponse -> Mono.just(Problem.valueOf(Status.INTERNAL_SERVER_ERROR)))
			.build();

		assertThat(webClient).extracting("defaultStatusHandlers").asList().hasSize(1);
	}

	@Test
	void testBuildWithServiceType() {
		final var builder = createBuilder(false);

		final var mockWebClientAdapter = mock(WebClientAdapter.class);
		final var mockHttpServiceProxyFactoryBuilder = mock(HttpServiceProxyFactory.Builder.class);
		final var mockHttpServiceProxyFactory = mock(HttpServiceProxyFactory.class);

		try (var mockStaticWebClientAdapter = mockStatic(WebClientAdapter.class);
			var mockStaticHttpServiceProxyFactory = mockStatic(HttpServiceProxyFactory.class)) {
			mockStaticWebClientAdapter.when(() -> WebClientAdapter.create(any(WebClient.class)))
				.thenReturn(mockWebClientAdapter);
			mockStaticHttpServiceProxyFactory.when(() -> HttpServiceProxyFactory.builderFor(any(HttpExchangeAdapter.class)))
				.thenReturn(mockHttpServiceProxyFactoryBuilder);

			when(mockHttpServiceProxyFactoryBuilder.build()).thenReturn(mockHttpServiceProxyFactory);
			when(mockHttpServiceProxyFactory.createClient(DummyClient.class)).thenReturn(() -> ResponseEntity.ok(""));

			final var client = builder.build(DummyClient.class);
			assertThat(client).isNotNull();
		}
	}

	private void assertTimeoutSetting(WebClient webClient, int timeoutMillis) {
		assertThat(webClient)
			.extracting("builder").extracting("connector").extracting("httpClient").extracting("config")
			.extracting("options").asInstanceOf(InstanceOfAssertFactories.MAP).containsEntry(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis);
	}

	private WebClientBuilder createBuilder(boolean populateValues) {
		return createBuilder(populateValues, populateValues, populateValues);
	}

	private WebClientBuilder createBuilder(boolean populateValues, boolean useBasicAuth, boolean useOauth2) {
		if (!populateValues) {
			return new WebClientBuilder();
		}

		final var builder = new WebClientBuilder()
			.withBaseUrl(BASE_URL)
			.withConnectTimeout(CONNECT_TIMEOUT)
			.withLogbook(LOGBOOK_MOCK)
			.withReadTimeout(READ_TIMEOUT)
			.withWriteTimeout(WRITE_TIMEOUT);

		if (useBasicAuth) {
			builder.withBasicAuthentication(USER_NAME, PASSWORD);

		}
		if (useOauth2) {
			final var mockBuilder = mock(ClientRegistration.Builder.class);
			try (var mock = mockStatic(ClientRegistration.class)) {
				mock.when(() -> ClientRegistration.withClientRegistration(any(ClientRegistration.class)))
					.thenReturn(mockBuilder);

				when(mockBuilder.scope(ArgumentMatchers.<Set<String>>any())).thenReturn(mockBuilder);
				when(mockBuilder.build()).thenReturn(CLIENT_REGISTRATION_MOCK);

				builder.withOAuth2ClientRegistration(CLIENT_REGISTRATION_MOCK);
			}
		}

		return builder;
	}

	interface DummyClient {

		@GetExchange("/something")
		ResponseEntity<String> getSomething();
	}
}
