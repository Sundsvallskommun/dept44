package se.sundsvall.dept44.scheduling.health;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

/**
 * Composite health contributor for schedulers using the {@link Dept44Scheduled} annotation.
 * <p>
 * This class implements the {@link CompositeHealthContributor} interface to provide health status for scheduled tasks.
 * It aggregates health indicators for all scheduled tasks.
 * </p>
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
 * </p>
 *
 * @see Dept44Scheduled
 * @see Dept44HealthIndicator
 * @see org.springframework.boot.health.contributor.CompositeHealthContributor
 * @see org.springframework.boot.health.contributor.HealthContributor
 */
@Component("dept44CompositeSchedulerHealthContributor")
public class Dept44CompositeHealthContributor implements CompositeHealthContributor {

	private final Map<String, Dept44HealthIndicator> indicators = new ConcurrentHashMap<>();

	/**
	 * Get the health contributor with the specified name.
	 *
	 * @param  name the name of the health contributor to get
	 * @return      the health contributor
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
	 * Get an iterator over the health contributors.
	 * <p>
	 * This method returns an iterator over the health contributors, allowing iteration through all the
	 * {@link HealthContributors} instances that represent the health indicators for the scheduled tasks.
	 * </p>
	 *
	 * @return an iterator over the health contributors
	 */
	@Override
	public Iterator<HealthContributors.Entry> iterator() {
		return indicators.entrySet().stream()
			.map(e -> new HealthContributors.Entry(e.getKey(), e.getValue()))
			.iterator();
	}

	@Override
	public Stream<Entry> stream() {
		return Stream.empty();
	}
}
