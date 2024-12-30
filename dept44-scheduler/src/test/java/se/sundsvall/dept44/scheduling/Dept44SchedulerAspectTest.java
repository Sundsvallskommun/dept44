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
		// Initialize health indicator for test methods
		aspect.getHealthIndicators().put("TestTask", new Dept44HealthIndicator());
	}

	@Test
	void testSetHealthIndicatorHealthy() {
		// arrange
		final String methodName = "TestTask";
		Dept44SchedulerAspect.setHealthIndicatorUnhealthy(methodName, "Initial error");

		// act
		Dept44SchedulerAspect.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName).health().getStatus().getCode()).isEqualTo("UP");
	}

	// Test setting the health indicator to healthy when it is already healthy
	@Test
	void testSetHealthIndicatorHealthyWhenAlreadyHealthy() {
		// arrange
		final String methodName = "TestTask";
		Dept44SchedulerAspect.setHealthIndicatorHealthy(methodName);

		// act
		Dept44SchedulerAspect.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName).health().getStatus().getCode()).isEqualTo("UP");
	}

	// Test setting the health indicator to unhealthy when it is already unhealthy
	@Test
	void testSetHealthIndicatorUnhealthyWhenAlreadyUnhealthy() {
		// arrange
		final String methodName = "TestTask";
		final String errorMessage = "Initial error";
		Dept44SchedulerAspect.setHealthIndicatorUnhealthy(methodName, errorMessage);

		// act
		Dept44SchedulerAspect.setHealthIndicatorUnhealthy(methodName, "Another error");

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName).health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(aspect.getHealthIndicators().get(methodName).health().getDetails()).containsEntry("Reason", "Another error");
	}

	// Test setting the health indicator to healthy for a non-existent method name
	@Test
	void testSetHealthIndicatorHealthyForNonExistentMethod() {
		// arrange
		final String methodName = "NonExistentTask";

		// act
		Dept44SchedulerAspect.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName)).isNull();
	}

	@Test
	void testSetHealthIndicatorUnhealthy() {
		// arrange
		final String methodName = "TestTask";
		final String errorMessage = "Test error";

		// act
		Dept44SchedulerAspect.setHealthIndicatorUnhealthy(methodName, errorMessage);

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName).health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(aspect.getHealthIndicators().get(methodName).health().getDetails()).containsEntry("Reason", errorMessage);
		assertThat(aspect.getLastFailure()).isNotNull();
	}

	// Test setting the health indicator to unhealthy for a non-existent method name
	@Test
	void testSetHealthIndicatorUnhealthyForNonExistentMethod() {
		// arrange
		final String methodName = "NonExistentTask";
		final String errorMessage = "Test error";

		// act
		Dept44SchedulerAspect.setHealthIndicatorUnhealthy(methodName, errorMessage);

		// assert
		assertThat(aspect.getHealthIndicators().get(methodName)).isNull();
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
		assertThat(aspect.getLastFailure()).isBefore(aspect.getLastSuccess());
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
		assertThat(aspect.getLastSuccess()).isBefore(aspect.getLastFailure());
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
