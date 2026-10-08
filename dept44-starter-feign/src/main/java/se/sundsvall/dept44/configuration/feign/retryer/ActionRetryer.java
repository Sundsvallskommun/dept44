package se.sundsvall.dept44.configuration.feign.retryer;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.util.Collection;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Retries a request whose token was rejected (HTTP 401, as a {@code RetryableException} from an error decoder's
 * {@code RetryResponseVerifier}), after running the action, such as evicting the rejected token.
 * <p>
 * Feign also reports I/O errors (connect and read timeouts) as a {@code RetryableException}, and its default error
 * decoder reports a response with a {@code Retry-After} header as one. Those are not retried: the request may already
 * have been processed (a timed-out POST would be sent twice), and a new token would not help.
 */
public class ActionRetryer implements Retryer {

	private static final int UNAUTHORIZED = 401;

	private final int maxAttempts;
	private int attempt;
	private final Action action;

	public ActionRetryer(Action action, int maxAttempts) {
		this.action = action;
		this.maxAttempts = maxAttempts;
		this.attempt = 1;
	}

	@Override
	public void continueOrPropagate(RetryableException e) {
		if (attempt > maxAttempts || e.status() != UNAUTHORIZED) {
			throw e;
		}
		// Pass the Authorization header of the failed request so the action only acts on the exact token that failed.
		// This avoids evicting a token that a concurrent thread has already refreshed in the meantime.
		action.execute(extractAuthorizationHeader(e.request()));
		attempt++;
	}

	private static String extractAuthorizationHeader(final Request request) {
		if (request == null || request.headers() == null) {
			return null;
		}
		final Collection<String> values = request.headers().get(AUTHORIZATION);
		return (values == null || values.isEmpty()) ? null : values.iterator().next();
	}

	@Override
	public Retryer clone() {
		return new ActionRetryer(action, maxAttempts);
	}
}
