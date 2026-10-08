package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.RequestWritingStage;
import org.zalando.logbook.Logbook.ResponseProcessingStage;
import org.zalando.logbook.Logbook.ResponseWritingStage;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.RequestFilter;
import org.zalando.logbook.ResponseFilter;
import org.zalando.logbook.Sink;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.servlet.AsyncOnCompleteListenerWrapper;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.TRANSFER_ENCODING;

/**
 * Runs the filter with a real Logbook. What the sink receives as a body is exactly what was held in memory for it.
 */
class LogbookServletFilterTest {

	private static final int LIMIT = 100;
	private static final String OMITTED_JSON = "{\"bodyOmitted\":\"larger than 100 bytes\"}";

	private final CapturingSink sink = new CapturingSink();
	private final BodyCapturePolicy policy = new BodyCapturePolicy(LIMIT);
	private final LogbookServletFilter filter = new LogbookServletFilter(Logbook.builder()
		.strategy(new BodyCaptureStrategy(policy))
		.requestFilter(RequestFilter.none())
		.responseFilter(ResponseFilter.none())
		.sink(sink)
		.build(), policy);

	@Test
	void smallBodiesAreCaptured() throws Exception {
		final var request = request("application/json", body(50), true);
		final var response = new MockHttpServletResponse();
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, response, (req, res) -> {
			received.set(req.getInputStream().readAllBytes());
			write(res, "application/json", body(60), true);
		});

		assertThat(received.get()).isEqualTo(body(50));
		assertThat(sink.requestBody).isEqualTo(text(50));
		assertThat(sink.responseBody).isEqualTo(text(60));
		assertThat(response.getContentAsByteArray()).isEqualTo(body(60));
	}

	@Test
	void largeRequestOfKnownLengthIsNotRead() throws Exception {
		final var request = request("application/json", body(LIMIT + 1), true);
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT + 1));
		assertThat(sink.requestBody).isEmpty();
	}

	@Test
	void largeRequestOfUnknownLengthIsLoggedAsOmitted() throws Exception {
		final var request = request("application/json", body(LIMIT * 3), false);
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT * 3));
		assertThat(sink.requestBody).isEqualTo(OMITTED_JSON);
	}

	@Test
	void smallRequestOfUnknownLengthIsCaptured() throws Exception {
		final var request = request("application/json", body(LIMIT), false);
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT));
		assertThat(sink.requestBody).isEqualTo(text(LIMIT));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/x-www-form-urlencoded", "multipart/form-data; boundary=abc", "not a media type"
	})
	void bodiesParsedByTheContainerAreNeverRead(final String contentType) throws Exception {
		final var request = request(contentType, body(LIMIT * 3), false);
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT * 3));
		assertThat(sink.requestBody).isEmpty();
	}

	@Test
	void binaryResponseIsNotCaptured() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/file"), response, (req, res) -> write(res, "application/pdf", body(10), true));

		assertThat(sink.responseBody).isEmpty();
		assertThat(response.getContentAsByteArray()).isEqualTo(body(10));
	}

	@Test
	void attachmentResponseIsNotCaptured() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/file"), response, (req, res) -> {
			response.setHeader(CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"");
			write(res, "text/csv", body(10), false);
		});

		assertThat(sink.responseBody).isEmpty();
		assertThat(response.getContentAsByteArray()).isEqualTo(body(10));
	}

	@Test
	void largeResponseOfKnownLengthIsNotCaptured() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/json"), response, (req, res) -> write(res, "application/json", body(LIMIT + 1), true));

		assertThat(sink.responseBody).isEmpty();
		assertThat(response.getContentAsByteArray()).isEqualTo(body(LIMIT + 1));
	}

	@Test
	void largeResponseOfUnknownLengthStopsBeingCopiedAtTheLimit() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/json"), response, (req, res) -> {
			res.setContentType("application/json");
			final var output = res.getOutputStream();
			for (var chunk = 0; chunk < 3; chunk++) {
				output.write(body(LIMIT));
			}
		});

		assertThat(sink.responseBody).isEqualTo(OMITTED_JSON);
		assertThat(response.getContentAsByteArray()).isEqualTo(body(LIMIT * 3));
	}

	@Test
	void responseWrittenThroughWriterIsCaptured() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/json"), response, (req, res) -> {
			res.setContentType("application/json");
			res.setCharacterEncoding("UTF-8");
			res.getWriter().write(text(20));
		});

		assertThat(sink.responseBody).isEqualTo(text(20));
		assertThat(response.getContentAsString()).isEqualTo(text(20));
	}

	@Test
	void asynchronousResponseIsLoggedWhenItCompletes() throws Exception {
		final var request = new MockHttpServletRequest("GET", "/stream");
		request.setAsyncSupported(true);
		final var response = new MockHttpServletResponse();

		filter.doFilter(request, response, (req, res) -> {
			req.startAsync(req, res);
			write(res, "application/json", body(30), true);
		});

		assertThat(sink.responseWritten).isFalse();

		// Timeouts and errors do not write anything; only completion does. A restart re-registers the listener.
		final var asyncContext = (MockAsyncContext) request.getAsyncContext();
		final var restarted = new MockAsyncContext(request, response);
		for (final var listener : asyncContext.getListeners()) {
			listener.onTimeout(null);
			listener.onError(null);
			listener.onStartAsync(new AsyncEvent(restarted));
		}
		assertThat(sink.responseWritten).isFalse();
		assertThat(restarted.getListeners()).hasSameElementsAs(asyncContext.getListeners());

		asyncContext.complete();

		assertThat(sink.responseWritten).isTrue();
		assertThat(sink.responseBody).isEqualTo(text(30));
	}

	@Test
	void asynchronousDispatchPassesStraightThrough() throws Exception {
		final var request = new MockHttpServletRequest("GET", "/stream");
		request.setDispatcherType(DispatcherType.ASYNC);
		final var response = new MockHttpServletResponse();
		final var chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(sink.requestBody).isNull();
	}

	@Test
	void requestLogbookDoesNotProcessIsNeverRead() throws Exception {
		final var inactive = new LogbookServletFilter(Logbook.builder()
			.strategy(new BodyCaptureStrategy(policy))
			.sink(new CapturingSink() {
				@Override
				public boolean isActive() {
					return false;
				}
			})
			.build(), policy);
		final var request = request("application/json", body(LIMIT * 3), false);
		final var original = request.getInputStream();
		final var received = new AtomicReference<ServletInputStream>();

		inactive.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream()));

		assertThat(received.get()).isSameAs(original);
		assertThat(received.get().readAllBytes()).isEqualTo(body(LIMIT * 3));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/merge-patch+json", "application/hal+json", "application/soap+xml"
	})
	void structuredSyntaxSuffixBodiesAreCaptured(final String contentType) throws Exception {
		filter.doFilter(request(contentType, body(20), true), new MockHttpServletResponse(), (req, res) -> req.getInputStream().readAllBytes());

		assertThat(sink.requestBody).isEqualTo(text(20));
	}

	@Test
	void securityFilterWritesAndCapturesOnlyRejectedRequests() throws Exception {
		final var accepted = new AtomicReference<CapturingResponse>();
		final var securityFilter = LogbookServletFilter.secure(Logbook.builder()
			.requestFilter(RequestFilter.none())
			.responseFilter(ResponseFilter.none())
			.sink(sink)
			.build(), policy, AsyncOnCompleteListenerWrapper.identity());

		securityFilter.doFilter(request("application/json", body(10), true), new MockHttpServletResponse(), (req, res) -> {
			accepted.set((CapturingResponse) res);
			write(res, "application/json", body(10), true);
		});

		assertThat(sink.responseWritten).isFalse();
		assertThat(accepted.get().getBody()).isEmpty();

		final var rejected = new MockHttpServletResponse();
		final var received = new AtomicReference<byte[]>();
		securityFilter.doFilter(request("application/json", body(10), true), rejected, (req, res) -> {
			received.set(req.getInputStream().readAllBytes());
			((HttpServletResponse) res).setStatus(401);
			write(res, "application/problem+json", body(10), true);
		});

		assertThat(sink.responseWritten).isTrue();
		assertThat(sink.requestBody).isEmpty();
		assertThat(sink.responseBody).isEqualTo(text(10));
		assertThat(received.get()).isEqualTo(body(10));
		assertThat(rejected.getContentAsByteArray()).isEqualTo(body(10));
	}

	@Test
	void responseIsLoggedWhenTheChainThrows() {
		final var response = new MockHttpServletResponse();
		final var failure = new IllegalStateException("boom");

		assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/fail"), response, (req, res) -> {
			throw failure;
		})).isSameAs(failure);

		assertThat(sink.responseWritten).isTrue();
		assertThat(sink.responseStatus).isEqualTo(500);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.isCommitted()).isFalse();
	}

	@Test
	void failingLoggingDoesNotReplaceTheApplicationsException() {
		// Logbook itself catches what its own stages throw, so this one fails directly when the response is written
		final ResponseProcessingStage failingResponse = _ -> () -> {
			throw new IllegalStateException("logging failed");
		};
		final var failingFilter = new LogbookServletFilter(new Logbook() {
			@Override
			public RequestWritingStage process(final HttpRequest request) {
				return new RequestWritingStage() {
					@Override
					public ResponseProcessingStage write() {
						return failingResponse;
					}

					@Override
					public ResponseWritingStage process(final HttpResponse response) throws IOException {
						return failingResponse.process(response);
					}
				};
			}

			@Override
			public RequestWritingStage process(final HttpRequest request, final Strategy strategy) {
				return process(request);
			}
		}, policy);
		final var failure = new IllegalStateException("boom");

		assertThatThrownBy(() -> failingFilter.doFilter(new MockHttpServletRequest("GET", "/fail"), new MockHttpServletResponse(), (req, res) -> {
			throw failure;
		}))
			.isSameAs(failure)
			.satisfies(e -> assertThat(e.getSuppressed()).extracting(Throwable::getMessage).containsExactly("logging failed"));
	}

	@Test
	void asynchronousCompletionGoesThroughTheListenerWrapper() throws Exception {
		final var wrapped = new AtomicReference<Boolean>(false);
		final var wrappingFilter = new LogbookServletFilter(Logbook.builder()
			.strategy(new BodyCaptureStrategy(policy))
			.sink(sink)
			.build(), policy, listener -> event -> {
				wrapped.set(true);
				listener.onComplete(event);
			});
		final var request = new MockHttpServletRequest("GET", "/stream");
		request.setAsyncSupported(true);

		wrappingFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> req.startAsync(req, res));
		((MockAsyncContext) request.getAsyncContext()).complete();

		assertThat(wrapped.get()).isTrue();
		assertThat(sink.responseWritten).isTrue();
	}

	@Test
	void nonHttpRequestPassesThrough() throws Exception {
		final var request = mock(ServletRequest.class);
		final var response = mock(ServletResponse.class);
		final var chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	private static MockHttpServletRequest request(final String contentType, final byte[] content, final boolean knownLength) {
		final var request = new MockHttpServletRequest("POST", "/upload") {
			@Override
			public int getContentLength() {
				if (knownLength) {
					return content.length;
				}
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return getContentLength();
			}
		};
		request.setContentType(contentType);
		request.setContent(content);
		if (knownLength) {
			request.addHeader(CONTENT_LENGTH, content.length);
		} else {
			request.addHeader(TRANSFER_ENCODING, "chunked");
		}
		return request;
	}

	private static void write(final ServletResponse response, final String contentType, final byte[] content, final boolean withLength) throws IOException {
		response.setContentType(contentType);
		if (withLength) {
			response.setContentLength(content.length);
		}
		response.getOutputStream().write(content);
	}

	private static byte[] body(final int size) {
		final var body = new byte[size];
		Arrays.fill(body, (byte) 'a');
		return body;
	}

	private static String text(final int size) {
		return new String(body(size), UTF_8);
	}

	private static class CapturingSink implements Sink {

		private String requestBody;
		private String responseBody;
		private int responseStatus;
		private boolean responseWritten;

		@Override
		public void write(final Precorrelation precorrelation, final HttpRequest request) throws IOException {
			requestBody = request.getBodyAsString();
		}

		@Override
		public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) throws IOException {
			responseBody = response.getBodyAsString();
			responseStatus = response.getStatus();
			responseWritten = true;
		}
	}
}
