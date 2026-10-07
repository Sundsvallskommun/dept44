package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.RequestWritingStage;
import org.zalando.logbook.Logbook.ResponseWritingStage;
import org.zalando.logbook.Strategy;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodylessSecurityStrategy;

import static jakarta.servlet.DispatcherType.ASYNC;

/**
 * Payload logging for servlet requests, in place of Logbook's own servlet filter.
 * <p>
 * The flow is Logbook's: the request is logged before it is handled, the response once it is complete, also when it is
 * completed asynchronously. An asynchronous dispatch passes straight through, since the original dispatch's response is
 * the one that is written to and logged. A request Logbook does not process (excluded path, payload logging off) is
 * never
 * read or copied. What differs is how bodies are captured: {@link CapturingRequest} and
 * {@link CapturingResponse} never hold more of a body in memory than the {@link BodyCapturePolicy} allows. Logbook's
 * own wrappers buffer whole bodies, and once its response copy has started there is no way to stop it.
 */
public final class LogbookServletFilter implements Filter {

	private final Logbook logbook;
	private final BodyCapturePolicy policy;
	private final Strategy strategy;

	public LogbookServletFilter(final Logbook logbook, final BodyCapturePolicy policy) {
		this(logbook, policy, null);
	}

	/**
	 * @param strategy used instead of the strategy Logbook was built with, such as {@link BodylessSecurityStrategy} for
	 *                 the filter that logs requests rejected by security; {@code null} uses Logbook's own
	 */
	public LogbookServletFilter(final Logbook logbook, final BodyCapturePolicy policy, final Strategy strategy) {
		this.logbook = logbook;
		this.policy = policy;
		this.strategy = strategy;
	}

	@Override
	public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain chain) throws IOException, ServletException {
		if (!(servletRequest instanceof final HttpServletRequest httpRequest) || !(servletResponse instanceof final HttpServletResponse httpResponse)) {
			chain.doFilter(servletRequest, servletResponse);
			return;
		}

		if (httpRequest.getDispatcherType() == ASYNC) {
			// The body is written through the response of the original dispatch, which is the one that gets logged.
			// Wrapping it again would only copy the body a second time.
			chain.doFilter(httpRequest, httpResponse);
			return;
		}

		final var request = new CapturingRequest(httpRequest, policy);
		final var response = new CapturingResponse(httpResponse, policy, request.getProtocolVersion());
		final var writing = process(request).write().process(response);

		chain.doFilter(request, response);

		if (request.isAsyncStarted()) {
			request.getAsyncContext().addListener(new WriteOnComplete(response, writing));
			return;
		}
		write(response, writing);
	}

	private RequestWritingStage process(final CapturingRequest request) throws IOException {
		if (strategy == null) {
			return logbook.process(request);
		}
		return logbook.process(request, strategy);
	}

	private static void write(final CapturingResponse response, final ResponseWritingStage writing) throws IOException {
		try {
			response.flushBuffer();
		} catch (final IOException _) {
			// The client may be gone; log the response anyway
		}
		writing.write();
	}

	private static final class WriteOnComplete implements AsyncListener {

		private final CapturingResponse response;
		private final ResponseWritingStage writing;

		private WriteOnComplete(final CapturingResponse response, final ResponseWritingStage writing) {
			this.response = response;
			this.writing = writing;
		}

		@Override
		public void onComplete(final AsyncEvent event) throws IOException {
			write(response, writing);
		}

		@Override
		public void onTimeout(final AsyncEvent event) {
			// Logged when the request completes
		}

		@Override
		public void onError(final AsyncEvent event) {
			// Logged when the request completes
		}

		/**
		 * Starting asynchronous processing again removes the registered listeners, so this one registers itself again.
		 */
		@Override
		public void onStartAsync(final AsyncEvent event) {
			event.getAsyncContext().addListener(this);
		}
	}
}
