package se.sundsvall.dept44.configuration.feign.retryer;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import java.util.Collection;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

public class ActionRetryer implements Retryer {

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
		if (attempt > maxAttempts) {
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
