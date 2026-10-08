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
import java.util.function.IntPredicate;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.RequestWritingStage;
import org.zalando.logbook.Logbook.ResponseWritingStage;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.SecurityStrategy;
import org.zalando.logbook.servlet.AsyncOnCompleteListener;
import org.zalando.logbook.servlet.AsyncOnCompleteListenerWrapper;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static jakarta.servlet.DispatcherType.ASYNC;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * Payload logging for servlet requests, in place of Logbook's own servlet filter.
 * <p>
 * The flow is Logbook's: the request is logged before it is handled, the response once it is complete, also when it is
 * completed asynchronously or the request fails with an exception. An asynchronous dispatch passes straight through,
 * since the original dispatch's response is the one that is written to and logged. A request Logbook does not process
 * (excluded path, payload logging off) is never read or copied. What differs is how bodies are captured:
 * {@link CapturingRequest} and {@link CapturingResponse} never hold more of a body in memory than the
 * {@link BodyCapturePolicy} allows. Logbook's own wrappers buffer whole bodies, and once its response copy has started
 * there is no way to stop it.
 */
public final class LogbookServletFilter implements Filter {

	private static final IntPredicate ANY_STATUS = status -> true;
	private static final IntPredicate REJECTED_BY_SECURITY = status -> status == SC_UNAUTHORIZED || status == SC_FORBIDDEN;

	private final Logbook logbook;
	private final BodyCapturePolicy policy;
	private final Strategy strategy;
	private final IntPredicate capturesStatus;
	private final AsyncOnCompleteListenerWrapper asyncOnCompleteListenerWrapper;

	public LogbookServletFilter(final Logbook logbook, final BodyCapturePolicy policy) {
		this(logbook, policy, AsyncOnCompleteListenerWrapper.identity());
	}

	/**
	 * @param asyncOnCompleteListenerWrapper wraps the listener that logs an asynchronous response once it completes, such
	 *                                       as to restore context on the thread that completes it
	 */
	public LogbookServletFilter(final Logbook logbook, final BodyCapturePolicy policy, final AsyncOnCompleteListenerWrapper asyncOnCompleteListenerWrapper) {
		this(logbook, policy, null, ANY_STATUS, asyncOnCompleteListenerWrapper);
	}

	private LogbookServletFilter(final Logbook logbook, final BodyCapturePolicy policy, final Strategy strategy, final IntPredicate capturesStatus,
		final AsyncOnCompleteListenerWrapper asyncOnCompleteListenerWrapper) {
		this.logbook = logbook;
		this.policy = policy;
		this.strategy = strategy;
		this.capturesStatus = capturesStatus;
		this.asyncOnCompleteListenerWrapper = asyncOnCompleteListenerWrapper;
	}

	/**
	 * The filter that logs requests rejected by security, like Logbook's own secure filter: it runs for every request,
	 * but logs only responses with status 401 or 403, and copies the response body only for those.
	 */
	public static LogbookServletFilter secure(final Logbook logbook, final BodyCapturePolicy policy, final AsyncOnCompleteListenerWrapper asyncOnCompleteListenerWrapper) {
		return new LogbookServletFilter(logbook, policy, new SecurityStrategy(), REJECTED_BY_SECURITY, asyncOnCompleteListenerWrapper);
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
		final var response = new CapturingResponse(httpResponse, policy, request.getProtocolVersion(), capturesStatus);
		final var writing = process(request).write().process(response);

		try {
			chain.doFilter(request, response);
		} catch (final IOException | ServletException | RuntimeException e) {
			try {
				writeFailed(request, response, writing);
			} catch (final IOException | RuntimeException suppressed) {
				// A logging failure, such as one in a body filter or the sink, must not replace the application's exception
				e.addSuppressed(suppressed);
			}
			throw e;
		}

		if (request.isAsyncStarted()) {
			writeOnComplete(request, response, writing);
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

	private void writeOnComplete(final CapturingRequest request, final CapturingResponse response, final ResponseWritingStage writing) {
		request.getAsyncContext().addListener(new WriteOnComplete(asyncOnCompleteListenerWrapper.wrap(_ -> write(response, writing))));
	}

	/**
	 * Flushing here would commit the response before the container writes its error page, so the response is logged
	 * as it stands.
	 */
	private void writeFailed(final CapturingRequest request, final CapturingResponse response, final ResponseWritingStage writing) throws IOException {
		if (request.isAsyncStarted()) {
			writeOnComplete(request, response, writing);
			return;
		}
		response.failed();
		writing.write();
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

		private final AsyncOnCompleteListener onComplete;

		private WriteOnComplete(final AsyncOnCompleteListener onComplete) {
			this.onComplete = onComplete;
		}

		@Override
		public void onComplete(final AsyncEvent event) throws IOException {
			onComplete.onComplete(event);
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
