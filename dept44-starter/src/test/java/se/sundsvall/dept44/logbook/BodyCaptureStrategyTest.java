package se.sundsvall.dept44.logbook;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

class BodyCaptureStrategyTest {

	private final BodyCaptureStrategy strategy = new BodyCaptureStrategy(new BodyCapturePolicy(100));

	@Test
	void requestWithinLimitIsCaptured() throws IOException {
		final var request = request("application/json", "10");

		assertThat(strategy.process(request)).isSameAs(request);

		verify(request).withBody();
		verify(request, never()).withoutBody();
	}

	@Test
	void requestOverLimitIsNotCaptured() throws IOException {
		final var request = request("application/json", "101");

		assertThat(strategy.process(request)).isSameAs(request);

		verify(request).withoutBody();
		verify(request, never()).withBody();
	}

	@Test
	void binaryRequestIsNotCaptured() throws IOException {
		final var request = request("application/pdf", "10");

		strategy.process(request);

		verify(request).withoutBody();
	}

	@Test
	void responseWithinLimitIsCaptured() throws IOException {
		final var response = response("application/json", "10");

		assertThat(strategy.process(mock(HttpRequest.class), response)).isSameAs(response);

		verify(response).withBody();
		verify(response, never()).withoutBody();
	}

	@Test
	void responseOverLimitIsNotCaptured() throws IOException {
		final var response = response("application/json", "101");

		assertThat(strategy.process(mock(HttpRequest.class), response)).isSameAs(response);

		verify(response).withoutBody();
		verify(response, never()).withBody();
	}

	@Test
	void responseWithUnreadableHeadersIsNotCaptured() throws IOException {
		final var response = mock(HttpResponse.class);
		when(response.getHeaders()).thenThrow(new NullPointerException("header without a name"));
		when(response.withoutBody()).thenReturn(response);

		assertThat(strategy.process(mock(HttpRequest.class), response)).isSameAs(response);

		verify(response).withoutBody();
		verify(response, never()).withBody();
	}

	@Test
	void requestWithUnreadableHeadersIsNotCaptured() throws IOException {
		final var request = mock(HttpRequest.class);
		when(request.getHeaders()).thenThrow(new IllegalStateException("headers not readable"));
		when(request.withoutBody()).thenReturn(request);

		assertThat(strategy.process(request)).isSameAs(request);

		verify(request).withoutBody();
		verify(request, never()).withBody();
	}

	private static HttpRequest request(final String contentType, final String contentLength) throws IOException {
		final var request = mock(HttpRequest.class);
		when(request.getHeaders()).thenReturn(HttpHeaders.of(CONTENT_LENGTH, contentLength));
		when(request.getContentType()).thenReturn(contentType);
		when(request.withBody()).thenReturn(request);
		when(request.withoutBody()).thenReturn(request);
		return request;
	}

	private static HttpResponse response(final String contentType, final String contentLength) throws IOException {
		final var response = mock(HttpResponse.class);
		when(response.getHeaders()).thenReturn(HttpHeaders.of(CONTENT_LENGTH, contentLength));
		when(response.getContentType()).thenReturn(contentType);
		when(response.withBody()).thenReturn(response);
		when(response.withoutBody()).thenReturn(response);
		return response;
	}
}
