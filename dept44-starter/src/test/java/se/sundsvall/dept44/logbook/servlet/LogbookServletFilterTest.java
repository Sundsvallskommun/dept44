package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.RequestFilter;
import org.zalando.logbook.ResponseFilter;
import org.zalando.logbook.Sink;
import org.zalando.logbook.servlet.LogbookFilter;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.TRANSFER_ENCODING;
import static se.sundsvall.dept44.logbook.BodyCaptureStrategy.SKIP_BODY_CAPTURE_ATTRIBUTE;

/**
 * Runs the real Logbook servlet filter. A body that is not captured shows up as an empty body in the sink, because
 * Logbook never copied it.
 */
class LogbookServletFilterTest {

	private static final int LIMIT = 100;

	private final CapturingSink sink = new CapturingSink();
	private final BodyCapturePolicy policy = new BodyCapturePolicy(LIMIT);
	private final LogbookServletFilter filter = new LogbookServletFilter(new LogbookFilter(Logbook.builder()
		.strategy(new BodyCaptureStrategy(policy))
		.requestFilter(RequestFilter.none())
		.responseFilter(ResponseFilter.none())
		.sink(sink)
		.build()), policy);

	@Test
	void smallBodiesAreCaptured() throws Exception {
		final var request = jsonRequest(body(50));
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
	void largeRequestIsNotCaptured() throws Exception {
		final var request = jsonRequest(body(LIMIT + 1));
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT + 1));
		assertThat(sink.requestBody).isEmpty();
	}

	@Test
	void largeChunkedRequestIsNotCaptured() throws Exception {
		final var request = chunkedJsonRequest(body(LIMIT * 3));
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT * 3));
		assertThat(sink.requestBody).isEmpty();
		assertThat(request.getAttribute(SKIP_BODY_CAPTURE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
	}

	@Test
	void smallChunkedRequestIsCaptured() throws Exception {
		final var request = chunkedJsonRequest(body(LIMIT));
		final var received = new AtomicReference<byte[]>();

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> received.set(req.getInputStream().readAllBytes()));

		assertThat(received.get()).isEqualTo(body(LIMIT));
		assertThat(sink.requestBody).isEqualTo(text(LIMIT));
		assertThat(request.getAttribute(SKIP_BODY_CAPTURE_ATTRIBUTE)).isNull();
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
	void largeResponseIsNotCaptured() throws Exception {
		final var response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest("GET", "/json"), response, (req, res) -> write(res, "application/json", body(LIMIT + 1), true));

		assertThat(sink.responseBody).isEmpty();
		assertThat(response.getContentAsByteArray()).isEqualTo(body(LIMIT + 1));
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
	void nonHttpRequestPassesThrough() throws Exception {
		final var request = mock(ServletRequest.class);
		final var response = mock(ServletResponse.class);
		final var chain = mock(jakarta.servlet.FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	private static MockHttpServletRequest jsonRequest(final byte[] content) {
		final var request = new MockHttpServletRequest("POST", "/json");
		request.setContentType("application/json");
		request.setContent(content);
		request.addHeader(CONTENT_LENGTH, content.length);
		return request;
	}

	private static MockHttpServletRequest chunkedJsonRequest(final byte[] content) {
		final var request = new MockHttpServletRequest("POST", "/json") {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContentType("application/json");
		request.setContent(content);
		request.addHeader(TRANSFER_ENCODING, "chunked");
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

	private static final class CapturingSink implements Sink {

		private String requestBody;
		private String responseBody;

		@Override
		public void write(final Precorrelation precorrelation, final HttpRequest request) throws IOException {
			requestBody = request.getBodyAsString();
		}

		@Override
		public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) throws IOException {
			responseBody = response.getBodyAsString();
		}
	}
}
