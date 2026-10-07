package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import org.zalando.logbook.HttpResponse;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

/**
 * Wraps the response that Logbook hands down the filter chain, and decides whether payload logging may capture the
 * body at the moment the body starts being written.
 * <p>
 * Logbook decides whether to capture a response body before the request is handled, when the response headers are
 * not yet known, and from then on copies every byte written into memory. By the time the body is written, the content
 * type, content disposition and often the length are set. A binary, attachment or oversized body is then excluded
 * before the first byte is copied.
 */
final class BodyCaptureResponseWrapper extends HttpServletResponseWrapper {

	private final BodyCapturePolicy policy;
	private boolean decided;

	BodyCaptureResponseWrapper(final HttpServletResponse response, final BodyCapturePolicy policy) {
		super(response);
		this.policy = policy;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		decide();
		return super.getOutputStream();
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		decide();
		return super.getWriter();
	}

	private void decide() {
		if (decided) {
			return;
		}
		decided = true;

		final var contentLength = BodyCapturePolicy.parseLength(getHeader(CONTENT_LENGTH));
		if (!policy.allowsCapture(getContentType(), getHeaders(CONTENT_DISPOSITION), contentLength)) {
			findLogbookResponse(getResponse()).ifPresent(HttpResponse::withoutBody);
		}
	}

	static Optional<HttpResponse> findLogbookResponse(final ServletResponse response) {
		var current = response;
		while (current != null) {
			if (current instanceof final HttpResponse logbookResponse) {
				return Optional.of(logbookResponse);
			}
			if (!(current instanceof final ServletResponseWrapper wrapper)) {
				return Optional.empty();
			}
			current = wrapper.getResponse();
		}
		return Optional.empty();
	}
}
