package se.sundsvall.dept44.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.requestid.RequestId;

/**
 * Aspect for handling scheduled methods via {@link Dep44Scheduled}.
 * <p>
 * Captures and logs any exceptions, sets a unique {@link RequestId} for each run, and updates last-success/last-failure
 * timestamps for slick health tracking. Also registers a separate {@link Dept44HealthIndicator} per method so you can
 * see at a glance how
 * each scheduled task is doing on the /actuator/health endpoint.
 * <p>
 * <strong>Usage:</strong>
 * <ul>
 * <li>Annotate your scheduled method with {@link Dep44Scheduled}</li>
 * <li>Add a config class that includes component scanning for "se.sundsvall.dept44.scheduling"</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> This relies on the method using the annotation to bubble up exceptions to the aspect. If you
 * catch and handle exceptions in the method, they won't be caught here, and the health indicator won't be updated.
 * </p>
 * <p>
 * <strong>Manual Health Checks:</strong> You can manually set the health status of a scheduled method using the
 * {@link #setHealthIndicatorHealthy(String)} and {@link #setHealthIndicatorUnhealthy(String, String)} methods.
 * </p>
 *
 * @see se.sundsvall.dept44.scheduling.Dep44Scheduled
 * @see se.sundsvall.dept44.scheduling.Dept44HealthIndicator
 * @see se.sundsvall.dept44.requestid.RequestId
 */

@Aspect
@Component
public class Dept44SchedulerAspect {

	private static final Logger LOG = LoggerFactory.getLogger(Dept44SchedulerAspect.class);
	private static final ConcurrentMap<String, Dept44HealthIndicator> healthIndicators = new ConcurrentHashMap<>();
	private static final AtomicReference<LocalDateTime> lastSuccess = new AtomicReference<>();
	private static final AtomicReference<LocalDateTime> lastFailure = new AtomicReference<>();

	/**
	 * Set the health indicator to healthy for the given method name.
	 *
	 * @param methodName the name of the scheduled method e.g. the name attribute of the {@link Dep44Scheduled} annotation
	 */
	public static void setHealthIndicatorHealthy(final String methodName) {
		final var healthIndicator = healthIndicators.get(methodName);
		if (healthIndicator != null) {
			healthIndicator.setHealthy();
		}
	}

	/**
	 * Set the health indicator to unhealthy for the given method name.
	 *
	 * @param methodName   the name of the scheduled method e.g. the name attribute of the {@link Dep44Scheduled} annotation
	 * @param errorMessage the error message to set
	 */
	public static void setHealthIndicatorUnhealthy(final String methodName, final String errorMessage) {
		final var healthIndicator = healthIndicators.get(methodName);
		if (healthIndicator != null) {
			healthIndicator.setUnhealthy(errorMessage);
			lastFailure.set(LocalDateTime.now());

		}
	}

	/**
	 * Get the timestamp of the last success.
	 *
	 * @return the timestamp of the last success
	 */
	public LocalDateTime getLastSuccess() {
		return lastSuccess.get();
	}

	/**
	 * Get the timestamp of the last failure.
	 *
	 * @return the timestamp of the last failure
	 */
	public LocalDateTime getLastFailure() {
		return lastFailure.get();
	}

	/**
	 * Get the health indicators for scheduled methods.
	 *
	 * @return a map of health indicators
	 */
	public Map<String, Dept44HealthIndicator> getHealthIndicators() {
		return healthIndicators;
	}

	/**
	 * Around advice for scheduled methods annotated with {@link Dep44Scheduled}.
	 * <p>
	 * This method sets a unique {@link RequestId} for each run, logs start and end of the method, and updates
	 * last-success/last-failure timestamps for slick health tracking. It also registers a separate
	 * {@link Dept44HealthIndicator} per method so you can
	 * see at a glance how each scheduled task is doing on the /actuator/health endpoint.
	 * <p>
	 * <strong>Note:</strong> This relies on the method using the annotation to bubble up exceptions to the
	 * aspect. If you catch and handle exceptions in the method, they won't be caught here, and the health indicator won't
	 * be updated. If you need to catch and handle exceptions in the method, you can manually set the health status using
	 * the
	 * {@link #setHealthIndicatorHealthy(String)} and {@link #setHealthIndicatorUnhealthy(String, String)} methods.
	 * </p>
	 * <p>
	 * <strong>Usage:</strong>
	 * <ul>
	 * <li>Annotate your scheduled method with {@link Dep44Scheduled}</li>
	 * <li>Add a config class that includes component scanning for "se.sundsvall.dept44.scheduling"</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <strong>Example:</strong>
	 *
	 * <pre>
	 * &#64;Dep44Scheduled(
	 * 	cron = "${schedulers.update-garbage-schedules.cron}",
	 * 	name = "UpdateGarbageSchedules",
	 * 	lockAtMostFor = "${schedulers.update-garbage-schedules.shedlock-lock-at-most-for}")
	 * public void doSomething() {
	 * 	doSomething();
	 * }
	 * </pre>
	 * </p>
	 *
	 * @param  pjp            the {@link ProceedingJoinPoint} for the scheduled method
	 * @param  dep44Scheduled the {@link Dep44Scheduled} annotation for the scheduled method
	 * @return                the result of the scheduled method
	 * @throws Throwable      if the scheduled method throws an exception
	 */
	@Around("@annotation(dep44Scheduled)")
	public Object aroundScheduledMethod(final ProceedingJoinPoint pjp, final Dep44Scheduled dep44Scheduled) throws Throwable {

		final var name = dep44Scheduled.name();
		final var healthIndicator = healthIndicators.computeIfAbsent(name, k -> new Dept44HealthIndicator());
		final var startTime = OffsetDateTime.now();

		try {
			RequestId.init();
			LOG.info("Scheduled method {} start. RequestID={}", name, RequestId.get());
			healthIndicator.resetErrors();
			final var result = pjp.proceed();
			lastSuccess.set(LocalDateTime.now());
			LOG.info("Scheduled method {} done. RequestID={}", name, RequestId.get());
			return result;
		} catch (final Exception e) {
			lastFailure.set(LocalDateTime.now());
			healthIndicator.setUnhealthy(e.getMessage());
			LOG.error("Scheduled method {} fail. RequestID={}", name, RequestId.get(), e);
		} finally {
			final var endTime = OffsetDateTime.now();
			final var duration = Duration.between(startTime, endTime);
			if (duration.toMinutes() > dep44Scheduled.maximumExecutionTime()) {
				LOG.warn("Scheduled method {} took too long: {} minutes. RequestID={}", name, duration.toMinutes(), RequestId.get());
			}

			if (!healthIndicator.hasErrors()) {
				healthIndicator.setHealthy();
			}
			RequestId.reset();
		}
		return null;
	}
}
