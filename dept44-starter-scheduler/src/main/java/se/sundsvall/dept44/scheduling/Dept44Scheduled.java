package se.sundsvall.dept44.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Scheduled;
import se.sundsvall.dept44.scheduling.health.Dept44HealthIndicator;

/**
 * Custom annotation merging {@link Scheduled} and {@link SchedulerLock} for scheduling and locking tasks.
 * <p>
 * Used by {@link se.sundsvall.dept44.scheduling.Dept44SchedulerAspect}.
 *
 * @see org.springframework.scheduling.annotation.Scheduled
 * @see net.javacrumbs.shedlock.spring.annotation.SchedulerLock
 * @see se.sundsvall.dept44.scheduling.Dept44SchedulerAspect
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Scheduled
@SchedulerLock
public @interface Dept44Scheduled {

	/**
	 * Alias for {@link Scheduled#cron()}.
	 *
	 * @return the cron expression for the scheduled task
	 */
	@AliasFor(annotation = Scheduled.class, attribute = "cron")
	String cron() default "";

	/**
	 * Alias for {@link SchedulerLock#name()}.
	 *
	 * <p>
	 * Also used as the name of the scheduled task.
	 *
	 * @return the name of the lock
	 */
	@AliasFor(annotation = SchedulerLock.class, attribute = "name")
	String name() default "";

	/**
	 * Alias for {@link SchedulerLock#lockAtMostFor()}.
	 *
	 * @return the maximum time to hold the lock
	 */
	@AliasFor(annotation = SchedulerLock.class, attribute = "lockAtMostFor")
	String lockAtMostFor() default "";

	/**
	 * The maximum time for executing the task before the health status is set to restricted.
	 * <p>
	 * Default is 2 minutes.
	 *
	 * @return the maximum execution time
	 * @see    Dept44HealthIndicator
	 */
	String maximumExecutionTime() default "2";
}
