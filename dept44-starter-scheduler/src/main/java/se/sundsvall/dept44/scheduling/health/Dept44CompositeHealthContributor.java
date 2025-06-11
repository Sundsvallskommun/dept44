package se.sundsvall.dept44.scheduling.health;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

/**
 * Composite health contributor for schedulers using the {@link se.sundsvall.dept44.scheduling.Dept44Scheduled}
 * annotation.
 * <p>
 * This class implements the {@link org.springframework.boot.actuate.health.CompositeHealthContributor} interface to
 * provide health status for scheduled tasks. It aggregates health indicators for all scheduled tasks.
 * <p>
 * The health status is determined by the health status of the individual tasks:
 * <ul>
 * <li>The health status of the composite contributor is <code>"UP"</code> if all tasks are healthy.</li>
 * <li>The health status of the composite contributor is <code>"DOWN"</code> if any task is unhealthy.</li>
 * <li>The health status of the composite contributor is <code>"RESTRICTED"</code> if any task is restricted.</li>
 * <li>The health status of the composite contributor is <code>"OUT_OF_SERVICE"</code> if any task is out of
 * service.</li>
 * <li>The health status of the composite contributor is <code>"UNKNOWN"</code> if any task is unknown.</li>
 * </ul>
 *
 * @see Dept44Scheduled
 * @see Dept44HealthIndicator
 * @see org.springframework.boot.actuate.health.CompositeHealthContributor
 * @see org.springframework.boot.actuate.health.HealthContributor
 * @see org.springframework.boot.actuate.health.NamedContributor
 */
@Component("dept44CompositeSchedulerHealthContributor")
public class Dept44CompositeHealthContributor implements CompositeHealthContributor {

	private final Map<String, Dept44HealthIndicator> indicators = new ConcurrentHashMap<>();

	/**
	 * {@inheritDoc}
	 *
	 * Get the health contributor with the specified name.
	 */
	@Override
	public HealthContributor getContributor(final String name) {
		return indicators.get(name);
	}

	/**
	 * Get all health contributors.
	 *
	 * @return all health contributors
	 */
	public Map<String, HealthContributor> getContributors() {
		return Map.copyOf(indicators);
	}

	/**
	 * Get or create a health indicator with the specified method name.
	 *
	 * @param  methodName the method name
	 * @return            the health indicator
	 */
	public Dept44HealthIndicator getOrCreateIndicator(final String methodName) {
		return indicators.computeIfAbsent(methodName, k -> new Dept44HealthIndicator());
	}

	/**
	 * {@inheritDoc}
	 *
	 * Obtain an iterator over the health contributors.
	 * <p>
	 * This method returns an iterator over the health contributors, allowing iteration through all the
	 * {@link NamedContributor} instances that represent the health indicators for the scheduled tasks.
	 */
	@NotNull
	@Override
	public Iterator<NamedContributor<HealthContributor>> iterator() {
		return indicators.entrySet().stream()
			.map(entry -> NamedContributor.of(entry.getKey(), (HealthContributor) entry.getValue()))
			.iterator();
	}
}
