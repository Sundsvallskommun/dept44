package se.sundsvall.dept44.configuration.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.zalando.logbook.LogbookCreator.builder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Strategy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

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
		mockServer.enqueue(new MockResponse().setBody(JSON_RESPONSE).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));
	}

	@Test
	void testRequestWithoutAuthentication() throws Exception {
		WebClientBuilder builder = new WebClientBuilder().withBaseUrl(mockServer.url("/").url().toString());

		ServerResponse response = builder.build().get().retrieve().bodyToMono(ServerResponse.class).block();

		assertThat(response.getValue()).isEqualTo("mockedResponse");
		RecordedRequest request = mockServer.takeRequest();

		assertThat(request).isNotNull();
		assertThat(request.getHeader(AUTHORIZATION)).isNull();
	}

	@Test
	void testRequestWithBasicAuthentication() throws Exception {
		WebClientBuilder builder = new WebClientBuilder().withBaseUrl(mockServer.url("/").url().toString())
			.withBasicAuthentication("userName", "password");

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
}
