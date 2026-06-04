package se.sundsvall.dept44.async;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Autoconfiguration that exposes a single {@link MdcTaskDecorator} bean.
 * <p>
 * Spring Boot's {@code TaskExecutionAutoConfiguration} and {@code TaskSchedulingAutoConfiguration} pick up a unique
 * {@link TaskDecorator} bean and apply it to the autoconfigured {@code applicationTaskExecutor} and
 * {@code taskScheduler} respectively. Therefore, simply exposing the bean is enough for {@code @Async} (and
 * {@code @Dept44Scheduled}) worker threads to inherit the caller thread's MDC/identity - no executor wiring is needed
 * and any {@code spring.task.execution.*} properties are preserved.
 * <p>
 * Note that this configuration deliberately does <em>not</em> enable {@code @Async} globally; services keep declaring
 * {@code @EnableAsync} themselves. The decorator is applied to the executor regardless of whether {@code @Async} is
 * enabled.
 * <p>
 * <strong>Coverage limitation:</strong> the decorator is only wired into Spring Boot's autoconfigured
 * {@code applicationTaskExecutor} (and {@code taskScheduler}). A service that declares its own {@code Executor}/
 * {@code TaskExecutor} bean makes Spring Boot's {@code applicationTaskExecutor} back off, so the decorator has nothing
 * to
 * attach to - the same applies to {@code @Async("someNamedExecutor")} pointing at a custom executor. Such services must
 * set {@link TaskDecorator} on their own executor (e.g. {@code executor.setTaskDecorator(new MdcTaskDecorator())}) to
 * get MDC propagation.
 * <p>
 * Can be disabled with {@code dept44.async.mdc.enabled=false}, and replaced by declaring a custom
 * {@link TaskDecorator} bean.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "dept44.async.mdc.enabled", havingValue = "true", matchIfMissing = true)
public class MdcTaskDecoratorConfiguration {

	@Bean
	@ConditionalOnMissingBean(TaskDecorator.class)
	TaskDecorator mdcTaskDecorator() {
		return new MdcTaskDecorator();
	}
}
