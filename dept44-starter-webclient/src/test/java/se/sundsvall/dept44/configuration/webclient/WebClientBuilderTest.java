package se.sundsvall.dept44.configuration.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;

import io.netty.channel.ChannelOption;
import se.sundsvall.dept44.support.BasicAuthentication;

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
			.hasAllNullFieldsOrPropertiesExcept("connectTimeout", "readTimeout", "writeTimeout")
			.hasFieldOrPropertyWithValue("connectTimeout", Duration.ofSeconds(10))
			.hasFieldOrPropertyWithValue("readTimeout", Duration.ofSeconds(30))
			.hasFieldOrPropertyWithValue("writeTimeout", Duration.ofSeconds(30));
	}

	@Test
	void testCreatePatterns() {
		WebClientBuilder builder = createBuilder(true);

		assertThat(builder)
			.hasFieldOrPropertyWithValue("baseUrl", BASE_URL)
			.hasFieldOrPropertyWithValue("basicAuthentication", new BasicAuthentication(USER_NAME, PASSWORD))
			.hasFieldOrPropertyWithValue("connectTimeout", CONNECT_TIMEOUT)
			.hasFieldOrPropertyWithValue("readTimeout", READ_TIMEOUT)
			.hasFieldOrPropertyWithValue("writeTimeout", WRITE_TIMEOUT)
			.hasFieldOrPropertyWithValue("logbook", LOGBOOK_MOCK)
			.hasFieldOrPropertyWithValue("oAuth2ClientRegistration", CLIENT_REGISTRATION_MOCK);
	}
	
	@Test
	void testValueRestrictions() {
		WebClientBuilder builder = createBuilder(false);
		
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(null))).hasMessage("baseUrl may not be blank.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(""))).hasMessage("baseUrl may not be blank.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(" "))).hasMessage("baseUrl may not be blank.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withConnectTimeout(null))).hasMessage("connectTimeout may not be null.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withReadTimeout(null))).hasMessage("readTimeout may not be null.");
		assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withWriteTimeout(null))).hasMessage("writeTimeout may not be null.");
	}
	
	@Test
	void testBuildFromMinimumValues() {
		WebClient webClient = createBuilder(false).withBaseUrl(BASE_URL).build();

		assertTimeoutSetting(webClient, 10000);
		assertThat(webClient).extracting("defaultHeaders").isNull();
		assertThat(webClient).extracting("builder").extracting("baseUrl").asString().isEqualTo(BASE_URL);
		assertThat(webClient).extracting("builder").extracting("filters").asList()
			.hasSize(1)
			.hasOnlyElementsOfType(RequestIdExchangeFilterFunction.class);
	}

	@Test
	void testBuildFromCustomValuesWithBasicAuth() {
		WebClient webClient = createBuilder(true, true, false).build();

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
		
		WebClient webClient = createBuilder(true, false, true).build();
		
		assertTimeoutSetting(webClient, 54000);
		assertThat(webClient).extracting("defaultHeaders").isNull();
		assertThat(webClient).extracting("builder").extracting("baseUrl").asString().isEqualTo(BASE_URL);
		assertThat(webClient).extracting("builder").extracting("filters").asList()
			.hasSize(2)
			.hasAtLeastOneElementOfType(RequestIdExchangeFilterFunction.class)
			.hasAtLeastOneElementOfType(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
	}
	
	@Test
	void testBuildWithIncompatibleSettings() {
		WebClientBuilder builder = createBuilder(true);
		assertThat(assertThrows(IllegalStateException.class, () -> builder.build())).hasMessage("Basic Auth and OAuth2 cannot be used simultaneously");
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
		if (!populateValues) return new WebClientBuilder();
		
		WebClientBuilder builder = new WebClientBuilder()
			.withBaseUrl(BASE_URL)
			.withConnectTimeout(CONNECT_TIMEOUT)
			.withLogbook(LOGBOOK_MOCK)
			.withReadTimeout(READ_TIMEOUT)
			.withWriteTimeout(WRITE_TIMEOUT);

		if (useBasicAuth) {
			builder.withBasicAuthentication(USER_NAME, PASSWORD);
			
		}
		if (useOauth2) {
			builder.withOAuth2Client(CLIENT_REGISTRATION_MOCK);
		}
		
		return builder;
	}
}
