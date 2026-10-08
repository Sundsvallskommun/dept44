package se.sundsvall.dept44.scheduling.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

/**
 * Health indicator for schedulers using the {@link Dept44Scheduled} annotation.
 *
 * <p>
 * This class implements the {@link HealthIndicator} interface to provide health status for scheduled tasks. It
 * indicates whether the scheduled task is healthy or not based on internal state.
 * </p>
 *
 * <p>
 * The health status is determined by the <code>healthy</code> and <code>errors</code> flags. If <code>healthy</code> is
 * true, the health status is <code>"UP"</code>. If <code>healthy</code> is false, the health status is
 * <code>"RESTRICTED"</code> with
 * an optional reason.
 * </p>
 *
 * <p>
 * Methods are provided to set the health status to healthy or unhealthy, and to reset error states. The health status
 * can be queried using the <code>health</code> method.
 * </p>
 *
 * <p>
 * The class uses {@link AtomicBoolean} to track the health and error states. The <code>reason</code> field provides
 * additional context when the health status is <code>"RESTRICTED"</code>.
 * </p>
 *
 * @see Dept44Scheduled
 * @see org.springframework.boot.health.contributor.HealthIndicator
 * @see org.springframework.boot.health.contributor.Health
 */
public class Dept44HealthIndicator implements HealthIndicator {
	private final AtomicBoolean healthy = new AtomicBoolean(true);
	private final AtomicBoolean errors = new AtomicBoolean(false);
	private volatile String reason;
	private final Set<Run> runs = ConcurrentHashMap.newKeySet();

	/**
	 * Get the health status. A run that is still going on after its maximum execution time makes the status
	 * {@code "RESTRICTED"} while it runs, so that a hung task is visible before (or without) it ever returning.
	 *
	 * @return the health status
	 */
	@Override
	public Health health() {
		final var now = Instant.now();
		final var overdueSince = runs.stream()
			.filter(run -> run.isOverdue(now))
			.map(Run::since)
			.min(Comparator.naturalOrder());
		if (overdueSince.isPresent()) {
			return Health.status("RESTRICTED")
				.withDetail("Reason", "Maximum execution time exceeded, still running since " + overdueSince.get())
				.build();
		}
		if (healthy.get()) {
			return Health.up().build();
		}
		return Health.status("RESTRICTED")
			.withDetail("Reason", Objects.requireNonNullElse(reason, "Unknown"))
			.build();
	}

	/**
	 * Set the health status to unhealthy.
	 */
	public void setUnhealthy() {
		healthy.set(false);
		errors.set(true);
	}

	/**
	 * Set the health status to unhealthy with a reason.
	 *
	 * @param reason the reason for the unhealthy status
	 */
	public void setUnhealthy(final String reason) {
		setUnhealthy();
		this.reason = reason;
	}

	/**
	 * Set the health status to healthy.
	 */
	public void setHealthy() {
		healthy.set(true);
		errors.set(false);
		this.reason = null;
	}

	/**
	 * Record that a run has started. Each run is tracked on its own, so that a run finishing does not hide another one
	 * of the same task that is still going on.
	 *
	 * @param  maximumExecutionTime how long the run may take before the status is {@code "RESTRICTED"}
	 * @return                      the run, to pass to {@link #runFinished(Run)}
	 */
	public Run runStarted(final Duration maximumExecutionTime) {
		final var run = new Run(Instant.now(), maximumExecutionTime);
		runs.add(run);
		return run;
	}

	/**
	 * Record that a run has finished.
	 *
	 * @param run the run returned by {@link #runStarted(Duration)}; {@code null} is ignored
	 */
	public void runFinished(final Run run) {
		if (run != null) {
			runs.remove(run);
		}
	}

	/**
	 * Reset the error state.
	 */
	public void resetErrors() {
		errors.set(false);
	}

	/**
	 * Check if the health indicator has errors.
	 *
	 * @return true if the health indicator has errors, false otherwise
	 */
	public boolean hasErrors() {
		return errors.get();
	}

	/**
	 * A run in progress. Compared by identity, so that two runs started at the same instant are still two runs.
	 */
	public static final class Run {

		private final Instant since;
		private final Duration maximumExecutionTime;

		private Run(final Instant since, final Duration maximumExecutionTime) {
			this.since = since;
			this.maximumExecutionTime = maximumExecutionTime;
		}

		Instant since() {
			return since;
		}

		boolean isOverdue(final Instant now) {
			return Duration.between(since, now).compareTo(maximumExecutionTime) > 0;
		}
	}
}
