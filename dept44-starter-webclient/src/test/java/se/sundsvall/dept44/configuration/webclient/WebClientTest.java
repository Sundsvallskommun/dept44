package se.sundsvall.dept44.configuration.webclient;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.zalando.logbook.LogbookCreator.builder;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Strategy;


import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class WebClientTest {

	private static final String JSON_RESPONSE = "{\"value\": \"mockedResponse\"}";

	private MockWebServer mockServer;

	@Spy
	private Logbook logbook = builder().build();

	@BeforeEach
	public void startServer() throws Exception {
		openMocks(this);

		mockServer = new MockWebServer();
		mockServer.start();

	}

	@Test
	void testRequestWithoutAuthentication() throws Exception {
		WebClientBuilder builder = new WebClientBuilder().withBaseUrl(mockServer.url("/").url().toString());

		mockServer.enqueue(new MockResponse().setBody(JSON_RESPONSE).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

		ServerResponse response = builder.build().get().retrieve().bodyToMono(ServerResponse.class).block();

		assertThat(response.getValue()).isEqualTo("mockedResponse");
		RecordedRequest request = mockServer.takeRequest();

		assertThat(request).isNotNull();
		assertThat(request.getHeader(AUTHORIZATION)).isNull();

	}

	@Test
	void testRequestWithOAuth2Retry() throws InterruptedException {
		WebClientBuilder builder = new WebClientBuilder()
			.withOAuth2ClientRegistration(createClientRegistration(mockServer.url("/token").url().toString()))
			.withBaseUrl(mockServer.url("/").url().toString());

		mockServer.enqueue(new MockResponse().setBody(createMockTokenResponse("mock-token-initial")).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).setResponseCode(200));
		mockServer.enqueue(new MockResponse().setResponseCode(401));
		mockServer.enqueue(new MockResponse().setBody(createMockTokenResponse("mock-token-retry")).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).setResponseCode(200));
		mockServer.enqueue(new MockResponse().setBody(JSON_RESPONSE).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

		ServerResponse response = builder.build().get().retrieve().bodyToMono(ServerResponse.class).block();

		assertThat(response.getValue()).isEqualTo("mockedResponse");

		var tokenInit = mockServer.takeRequest(2, TimeUnit.SECONDS);
		var firstCall = mockServer.takeRequest(2, TimeUnit.SECONDS);
		var tokenRetry = mockServer.takeRequest(2, TimeUnit.SECONDS);
		var successfulCall = mockServer.takeRequest(2, TimeUnit.SECONDS);

		assertThat(tokenInit.getPath()).isEqualTo("/token");
		assertThat(tokenInit.getMethod()).isEqualTo("POST");
		assertThat(tokenInit.getBody().readUtf8()).matches("grant_type=client_credentials&scope=device_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
		assertThat(tokenInit.getHeaders().get(AUTHORIZATION)).isEqualTo("Basic " + Base64.getEncoder().encodeToString("clientId:clientSecret".getBytes(StandardCharsets.UTF_8)));
		assertThat(firstCall.getPath()).isEqualTo("/");
		assertThat(firstCall.getMethod()).isEqualTo("GET");
		assertThat(firstCall.getBody().size()).isEqualTo(0);
		assertThat(firstCall.getHeaders().get(AUTHORIZATION)).isEqualTo("Bearer mock-token-initial");
		assertThat(tokenRetry.getPath()).isEqualTo("/token");
		assertThat(tokenRetry.getMethod()).isEqualTo("POST");
		assertThat(tokenRetry.getBody().readUtf8()).matches("grant_type=client_credentials&scope=device_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
		assertThat(tokenRetry.getHeaders().get(AUTHORIZATION)).isEqualTo("Basic " + Base64.getEncoder().encodeToString("clientId:clientSecret".getBytes(StandardCharsets.UTF_8)));
		assertThat(successfulCall.getPath()).isEqualTo("/");
		assertThat(successfulCall.getMethod()).isEqualTo("GET");
		assertThat(successfulCall.getBody().size()).isEqualTo(0);
		assertThat(successfulCall.getHeaders().get(AUTHORIZATION)).isEqualTo("Bearer mock-token-retry");
	}

	@Test
	void testRequestWithBasicAuthentication() throws Exception {
		WebClientBuilder builder = new WebClientBuilder().withBaseUrl(mockServer.url("/").url().toString())
			.withBasicAuthentication("userName", "password");

		mockServer.enqueue(new MockResponse().setBody(JSON_RESPONSE).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

		ServerResponse response = builder.build().get().retrieve().bodyToMono(ServerResponse.class).block();

    	assertThat(response.getValue()).isEqualTo("mockedResponse");
    	RecordedRequest request = mockServer.takeRequest();
    	
    	assertThat(request).isNotNull();
    	assertThat(request.getHeader(AUTHORIZATION)).isEqualTo("Basic dXNlck5hbWU6cGFzc3dvcmQ=");
    }

	@Test
	void testRequestWithLogbookEnabled() throws Exception {
		WebClientBuilder builder = new WebClientBuilder().withBaseUrl(mockServer.url("/").url().toString())
			.withLogbook(logbook);

		mockServer.enqueue(new MockResponse().setBody(JSON_RESPONSE).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

		ServerResponse response = builder.build().get().retrieve().bodyToMono(ServerResponse.class).block();

		assertThat(response.getValue()).isEqualTo("mockedResponse");
		RecordedRequest request = mockServer.takeRequest();

		assertThat(request).isNotNull();
		verify(logbook).process(any(HttpRequest.class), any(Strategy.class));
	}

	@AfterEach
	public void stopServer() throws Exception {
		mockServer.shutdown();
	}

	private static class ServerResponse {

		private String value;

		@SuppressWarnings("unused")
		public void setValue(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	private static ClientRegistration createClientRegistration(String url) {
		return createClientRegistration(url, emptySet());
	}

	private static ClientRegistration createClientRegistration(String url, final Set<String> scope) {
		return ClientRegistration
			.withRegistrationId("test")
			.clientId("clientId")
			.tokenUri(url)
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.scope(scope)
			.clientSecret("clientSecret")
			.build();
	}

	private static String createMockTokenResponse(String token) {

		return String.format("{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":3600}", token);
	}
}
