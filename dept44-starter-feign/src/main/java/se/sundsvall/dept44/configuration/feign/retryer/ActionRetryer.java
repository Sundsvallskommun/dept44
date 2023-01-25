package se.sundsvall.dept44.configuration.feign.retryer;

import feign.RetryableException;
import feign.Retryer;

public class ActionRetryer implements Retryer {

	private int maxAttempts;
	private int attempt;
	private Action action;

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
		action.execute();
		attempt++;
	}

	@Override
	public Retryer clone() {
		return new ActionRetryer(action, maxAttempts);
	}
}
