package se.sundsvall.dept44.logbook;

import java.io.IOException;
import org.zalando.logbook.HttpMessage;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Strategy;

/**
 * Logbook strategy that only captures bodies the {@link BodyCapturePolicy} allows.
 * <p>
 * The decision is made from the headers, before Logbook starts buffering, so a body that is not captured is never
 * held in memory. Requests and responses that are not captured are still logged, without their body. A body of unknown
 * length passes this check; the servlet wrappers and the Feign logger then bound it while it is read.
 */
public final class BodyCaptureStrategy implements Strategy {

	private final BodyCapturePolicy policy;

	public BodyCaptureStrategy(final BodyCapturePolicy policy) {
		this.policy = policy;
	}

	@Override
	public HttpRequest process(final HttpRequest request) throws IOException {
		if (!allowsCapture(request)) {
			return request.withoutBody();
		}
		return request.withBody();
	}

	@Override
	public HttpResponse process(final HttpRequest request, final HttpResponse response) throws IOException {
		if (allowsCapture(response)) {
			return response.withBody();
		}
		return response.withoutBody();
	}

	/**
	 * Reading the headers of a servlet response can fail while another thread completes an asynchronous response:
	 * Tomcat's header list is not thread-safe, and Logbook then sees a header without a name. Logbook tolerates that when
	 * writing the log line, but an exception thrown here would fail the request. When the headers cannot be read, the
	 * body is not captured.
	 */
	private boolean allowsCapture(final HttpMessage message) {
		try {
			return policy.allowsCapture(message);
		} catch (final RuntimeException _) {
			return false;
		}
	}
}
