package se.sundsvall.dept44.scheduling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import se.sundsvall.dept44.scheduling.health.Dept44CompositeHealthContributor;
import se.sundsvall.dept44.scheduling.health.Dept44HealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Dept44SchedulerAspectTest {

	@InjectMocks
	private Dept44SchedulerAspect aspect;

	@Mock
	private ProceedingJoinPoint pjp;

	@Mock
	private Dept44CompositeHealthContributor healthContributor;

	@Mock
	private Dept44Scheduled dept44Scheduled;

	@Mock
	private Environment environment;

	private Logger aspectLogger;
	private ListAppender<ILoggingEvent> appender;
	private Level originalLevel;

	@BeforeEach
	void setUp() {
		// Initialize health indicator for test methods
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());

		// Capture the aspect's log events. The MDC is snapshotted eagerly (see MdcCapturingListAppender) because the
		// aspect clears its MDC entries in a finally block before the assertions run.
		aspectLogger = (Logger) LoggerFactory.getLogger(Dept44SchedulerAspect.class);
		originalLevel = aspectLogger.getLevel();
		aspectLogger.setLevel(Level.TRACE);
		appender = new MdcCapturingListAppender();
		appender.start();
		aspectLogger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		aspectLogger.detachAppender(appender);
		appender.stop();
		aspectLogger.setLevel(originalLevel);
	}

	@Test
	void testAroundScheduledMethodSuccess() throws Throwable {
		// arrange
		when(environment.resolvePlaceholders("${scheduled.test-task.name}")).thenReturn("TestTask");
		when(environment.resolvePlaceholders("${scheduled.test-task.maximum-execution-time}")).thenReturn("PT2M");
		when(dept44Scheduled.name()).thenReturn("${scheduled.test-task.name}");
		when(dept44Scheduled.maximumExecutionTime()).thenReturn("${scheduled.test-task.maximum-execution-time}");
		when(pjp.proceed()).thenReturn("Success");

		// act
		final var result = aspect.aroundScheduledMethod(pjp, dept44Scheduled);

		// assert
		assertThat(result).isEqualTo("Success");
		assertThat(healthContributor.getOrCreateIndicator("TestTask").health().getStatus().getCode()).isEqualTo("UP");

		// assert structured MDC fields on the "done" log line
		final var doneMdc = mdcOf("done");
		assertThat(doneMdc).containsEntry("schedulerName", "TestTask");
		assertThat(doneMdc).containsEntry("outcome", "SUCCESS");
		assertThat(doneMdc.get("executionId")).isNotBlank();
		assertThat(UUID.fromString(doneMdc.get("executionId"))).isNotNull();
		assertThat(Long.parseLong(doneMdc.get("durationMs"))).isGreaterThanOrEqualTo(0L);

		// the start line carries identifiers but not yet duration/outcome
		final var startMdc = mdcOf("start");
		assertThat(startMdc).containsEntry("schedulerName", "TestTask");
		assertThat(startMdc.get("executionId")).isNotBlank();
		assertThat(startMdc).doesNotContainKeys("durationMs", "outcome");

		// MDC is cleared after the run (pooled scheduler threads must not leak fields)
		assertThat(MDC.get("schedulerName")).isNull();
		assertThat(MDC.get("executionId")).isNull();
		assertThat(MDC.get("durationMs")).isNull();
		assertThat(MDC.get("outcome")).isNull();
	}

	@Test
	void testAroundScheduledMethodFailure() throws Throwable {
		// arrange
		when(environment.resolvePlaceholders("TestTask")).thenReturn("TestTask");
		when(environment.resolvePlaceholders("PT2M")).thenReturn("PT2M");
		when(dept44Scheduled.name()).thenReturn("TestTask");
		when(dept44Scheduled.maximumExecutionTime()).thenReturn("PT2M");

		when(pjp.proceed()).thenThrow(new RuntimeException("Test exception"));

		// act
		aspect.aroundScheduledMethod(pjp, dept44Scheduled);

		// assert
		assertThat(healthContributor.getOrCreateIndicator("TestTask").health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(healthContributor.getOrCreateIndicator("TestTask").health().getDetails()).containsEntry("Reason", "Test exception");

		// assert structured MDC fields on the "fail" log line
		final var failMdc = mdcOf("fail");
		assertThat(failMdc).containsEntry("schedulerName", "TestTask");
		assertThat(failMdc).containsEntry("outcome", "FAILURE");
		assertThat(failMdc.get("executionId")).isNotBlank();
		assertThat(Long.parseLong(failMdc.get("durationMs"))).isGreaterThanOrEqualTo(0L);
	}

	@Test
	void testAroundScheduledMethodTimeout() throws Throwable {
		// arrange - a negative maximum execution time makes any elapsed time exceed it deterministically
		when(environment.resolvePlaceholders("TestTask")).thenReturn("TestTask");
		when(environment.resolvePlaceholders("PT-1S")).thenReturn("PT-1S");
		when(dept44Scheduled.name()).thenReturn("TestTask");
		when(dept44Scheduled.maximumExecutionTime()).thenReturn("PT-1S");
		when(pjp.proceed()).thenReturn("Success");

		// act
		aspect.aroundScheduledMethod(pjp, dept44Scheduled);

		// assert - health is restricted with the timeout reason
		assertThat(healthContributor.getOrCreateIndicator("TestTask").health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(healthContributor.getOrCreateIndicator("TestTask").health().getDetails()).containsEntry("Reason", "Maximum execution time exceeded");

		// assert structured MDC fields on the "took too long" (WARN) log line
		final var warnMdc = appender.list.stream()
			.filter(event -> event.getLevel() == Level.WARN)
			.findFirst()
			.orElseThrow()
			.getMDCPropertyMap();
		assertThat(warnMdc).containsEntry("schedulerName", "TestTask");
		assertThat(warnMdc).containsEntry("outcome", "TIMEOUT");
		assertThat(Long.parseLong(warnMdc.get("durationMs"))).isGreaterThanOrEqualTo(0L);
	}

	@Test
	void testAroundScheduledMethodWithNoErrors() throws Throwable {
		// arrange
		when(environment.resolvePlaceholders("TestTask")).thenReturn("TestTask");
		when(environment.resolvePlaceholders("PT2M")).thenReturn("PT2M");
		when(dept44Scheduled.name()).thenReturn("TestTask");
		when(dept44Scheduled.maximumExecutionTime()).thenReturn("PT2M");
		when(pjp.proceed()).thenReturn("Success");

		// act
		aspect.aroundScheduledMethod(pjp, dept44Scheduled);

		// assert
		assertThat(healthContributor.getOrCreateIndicator("TestTask").hasErrors()).isFalse();
	}

	@Test
	void testAroundScheduledMethodWithErrors() throws Throwable {
		// arrange
		when(environment.resolvePlaceholders("TestTask")).thenReturn("TestTask");
		when(environment.resolvePlaceholders("PT2M")).thenReturn("PT2M");
		when(dept44Scheduled.name()).thenReturn("TestTask");
		when(dept44Scheduled.maximumExecutionTime()).thenReturn("PT2M");

		when(pjp.proceed()).thenThrow(new RuntimeException("Test exception"));

		// act
		aspect.aroundScheduledMethod(pjp, dept44Scheduled);

		// assert
		assertThat(healthContributor.getOrCreateIndicator("TestTask").hasErrors()).isTrue();
	}

	private Map<String, String> mdcOf(final String messageFragment) {
		return appender.list.stream()
			.filter(event -> event.getFormattedMessage().contains(messageFragment))
			.findFirst()
			.orElseThrow()
			.getMDCPropertyMap();
	}

	/**
	 * {@link ListAppender} that eagerly snapshots the MDC of each event as it is appended. This is required because the
	 * aspect removes its MDC entries in a finally block, and {@link ILoggingEvent#getMDCPropertyMap()} is otherwise
	 * resolved lazily (i.e. after the MDC has already been cleared).
	 */
	private static class MdcCapturingListAppender extends ListAppender<ILoggingEvent> {
		@Override
		protected void append(final ILoggingEvent eventObject) {
			eventObject.getMDCPropertyMap();
			super.append(eventObject);
		}
	}
}
