package se.sundsvall.dept44.scheduling.health;

import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

/**
 * Utility class for setting the health of a scheduled method.
 *
 * <p>
 * This class is used to set the health of a scheduled method. It provides methods to set the health indicator to
 * healthy or unhealthy.
 *
 * <p>
 * Use this if you need to set the health of a scheduled method from within the method itself instead of relying on the
 * aspect to catch exceptions.
 */
@Component
public class Dept44HealthUtility {

	private final Dept44CompositeHealthContributor dept44Composite;

	public Dept44HealthUtility(final Dept44CompositeHealthContributor dept44Composite) {
		this.dept44Composite = dept44Composite;
	}

	/**
	 * Set the health indicator to healthy for the given method name.
	 *
	 * @param methodName the name of the scheduled method e.g. the name attribute of the {@link Dept44Scheduled} annotation
	 */
	public void setHealthIndicatorHealthy(final String methodName) {
		final var healthIndicator = dept44Composite.getOrCreateIndicator(methodName);
		if (healthIndicator != null) {
			healthIndicator.setHealthy();
		}
	}

	/**
	 * Set the health indicator to unhealthy for the given method name.
	 *
	 * @param methodName   the name of the scheduled method e.g. the name attribute of the {@link Dept44Scheduled}
	 *                     annotation
	 * @param errorMessage the error message to set
	 */
	public void setHealthIndicatorUnhealthy(final String methodName, final String errorMessage) {
		final var healthIndicator = dept44Composite.getOrCreateIndicator(methodName);
		if (healthIndicator != null) {
			healthIndicator.setUnhealthy(errorMessage);
		}
	}
}
