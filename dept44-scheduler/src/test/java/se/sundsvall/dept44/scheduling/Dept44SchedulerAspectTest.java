package se.sundsvall.dept44.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class Dept44SchedulerAspectTest {

	private Dept44SchedulerAspect aspect;

	@Mock
	private ProceedingJoinPoint pjp;

	@Mock
	private Dep44Scheduled dep44Scheduled;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		aspect = new Dept44SchedulerAspect();
	}

	@Test
	void testAroundScheduledMethodSuccess() throws Throwable {
		// arrange
		when(dep44Scheduled.name()).thenReturn("TestTask");
		when(pjp.proceed()).thenReturn("Success");

		// act
		final var result = aspect.aroundScheduledMethod(pjp, dep44Scheduled);

		// assert
		assertThat(result).isEqualTo("Success");
		assertThat(aspect.getLastSuccess()).isNotNull();
		assertThat(aspect.getLastFailure()).isNull();
		assertThat(aspect.getHealthIndicators().get("TestTask").health().getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testAroundScheduledMethodFailure() throws Throwable {
		// arrange
		when(dep44Scheduled.name()).thenReturn("TestTask");
		when(pjp.proceed()).thenThrow(new RuntimeException("Test exception"));

		// act
		aspect.aroundScheduledMethod(pjp, dep44Scheduled);

		// assert
		assertThat(aspect.getLastSuccess()).isNull();
		assertThat(aspect.getLastFailure()).isNotNull();
		assertThat(aspect.getHealthIndicators().get("TestTask").health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(aspect.getHealthIndicators().get("TestTask").health().getDetails()).containsEntry("Reason", "Test exception");
	}

	@Test
	void testAroundScheduledMethodWithNoErrors() throws Throwable {
		// arrange
		when(dep44Scheduled.name()).thenReturn("TestTask");
		when(pjp.proceed()).thenReturn("Success");

		// act
		aspect.aroundScheduledMethod(pjp, dep44Scheduled);

		// assert
		assertThat(aspect.getHealthIndicators().get("TestTask").hasErrors()).isFalse();
	}

	@Test
	void testAroundScheduledMethodWithErrors() throws Throwable {
		// arrange
		when(dep44Scheduled.name()).thenReturn("TestTask");
		when(pjp.proceed()).thenThrow(new RuntimeException("Test exception"));

		// act
		aspect.aroundScheduledMethod(pjp, dep44Scheduled);

		// assert
		assertThat(aspect.getHealthIndicators().get("TestTask").hasErrors()).isTrue();
	}
}
