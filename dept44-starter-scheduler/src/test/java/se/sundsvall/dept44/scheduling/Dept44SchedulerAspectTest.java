package se.sundsvall.dept44.scheduling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

	@BeforeEach
	void setUp() {
		// Initialize health indicator for test methods
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());
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
}
