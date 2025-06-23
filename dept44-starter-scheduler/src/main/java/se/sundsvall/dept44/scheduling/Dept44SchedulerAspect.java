package se.sundsvall.dept44.scheduling;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.scheduling.health.Dept44CompositeHealthContributor;
import se.sundsvall.dept44.scheduling.health.Dept44HealthIndicator;

/**
 * Aspect for handling scheduled methods via {@link Dept44Scheduled}.
 * <p>
 * Captures and logs any exceptions, sets a unique {@link RequestId} for each run, and updates last-success/last-failure
 * timestamps for slick health tracking. Also registers a separate {@link Dept44HealthIndicator} per method so you can
 * see at a glance how
 * each scheduled task is doing on the /actuator/health endpoint.
 * </p>
 * <p>
 * <strong>Usage:</strong>
 * <ul>
 * <li>Annotate your scheduled method with {@link Dept44Scheduled}</li>
 * <li>Add a config class that includes component scanning for "se.sundsvall.dept44.scheduling"</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Note:</strong> This relies on the method using the annotation to bubble up exceptions to the aspect. If you
 * catch and handle exceptions in the method, they won't be caught here, and the health indicator won't be updated.
 * </p>
 *
 * @see Dept44Scheduled
 * @see Dept44HealthIndicator
 * @see se.sundsvall.dept44.requestid.RequestId
 */

@Aspect
@Component
public class Dept44SchedulerAspect {

	private static final Logger LOG = LoggerFactory.getLogger(Dept44SchedulerAspect.class);
	private final Dept44CompositeHealthContributor dept44Composite;
	private final Environment environment;

	public Dept44SchedulerAspect(final Dept44CompositeHealthContributor dept44Composite, final Environment environment) {
		this.dept44Composite = dept44Composite;
		this.environment = environment;
	}

	/**
	 * Around advice for scheduled methods annotated with {@link Dept44Scheduled}.
	 * <p>
	 * This method sets a unique {@link RequestId} for each run, logs start and end of the method, and updates
	 * last-success/last-failure timestamps for slick health tracking. It also registers a separate
	 * {@link Dept44HealthIndicator} per method so you can
	 * see at a glance how each scheduled task is doing on the /actuator/health endpoint.
	 * </p>
	 * <p>
	 * <strong>Note:</strong> This relies on the method using the annotation to bubble up exceptions to the
	 * aspect. If you catch and handle exceptions in the method, they won't be caught here, and the health indicator won't
	 * be updated.
	 * </p>
	 * <p>
	 * <strong>Usage:</strong>
	 * <ul>
	 * <li>Annotate your scheduled method with {@link Dept44Scheduled}</li>
	 * <li>Add a config class that includes component scanning for "se.sundsvall.dept44.scheduling"</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <strong>Example:</strong>
	 *
	 * <pre>
	 * &#64;Dept44Scheduled(
	 * 	cron = "${schedulers.update-garbage-schedules.cron}",
	 * 	name = "UpdateGarbageSchedules",
	 * 	lockAtMostFor = "${schedulers.update-garbage-schedules.shedlock-lock-at-most-for}")
	 * public void doSomething() {
	 * 	doSomething();
	 * }
	 * </pre>
	 * </p>
	 *
	 * @param  pjp             the {@link ProceedingJoinPoint} for the scheduled method
	 * @param  dept44Scheduled the {@link Dept44Scheduled} annotation for the scheduled method
	 * @return                 the result of the scheduled method
	 * @throws Throwable       if the scheduled method throws an exception
	 */
	@Around("@annotation(dept44Scheduled)")
	public Object aroundScheduledMethod(final ProceedingJoinPoint pjp, final Dept44Scheduled dept44Scheduled) throws Throwable {

		final var name = environment.resolvePlaceholders(dept44Scheduled.name());
		final var maxExecutionTime = environment.resolvePlaceholders(dept44Scheduled.maximumExecutionTime());

		final var healthIndicator = dept44Composite.getOrCreateIndicator(name);
		final var startTime = OffsetDateTime.now();
		try {
			RequestId.init();
			LOG.info("Scheduled method {} start. RequestID={}", name, RequestId.get());
			healthIndicator.resetErrors();
			final var result = pjp.proceed();
			LOG.info("Scheduled method {} done. RequestID={}", name, RequestId.get());
			return result;
		} catch (final Exception e) {
			healthIndicator.setUnhealthy(e.getMessage());
			LOG.error("Scheduled method {} fail. RequestID={}", name, RequestId.get(), e);
		} finally {
			final var endTime = OffsetDateTime.now();
			final var duration = Duration.between(startTime, endTime);
			if (duration.compareTo(Duration.parse(maxExecutionTime)) > 0) {
				LOG.warn("Scheduled method {} took too long: {} minutes. RequestID={}", name, duration.toMinutes(), RequestId.get());
				healthIndicator.setUnhealthy("Maximum execution time exceeded");
			}

			if (!healthIndicator.hasErrors()) {
				healthIndicator.setHealthy();
			}
			RequestId.reset();
		}
		return null;
	}
}
