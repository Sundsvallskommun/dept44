package se.sundsvall.dept44.configuration.feign.retryer;

@FunctionalInterface
public interface Action {

	/**
	 * Executes the retry action for a failed request.
	 *
	 * @param failedAuthorizationHeader the value of the {@code Authorization} header carried by the request that failed
	 *                                  (may be {@code null} if the request had no such header), allowing the action to
	 *                                  act only on the exact token that failed.
	 */
	void execute(String failedAuthorizationHeader);
}
