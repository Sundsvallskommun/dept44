package se.sundsvall.dept44.scheduling.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Dept44HealthUtilityTest {

	@InjectMocks
	private Dept44HealthUtility dept44Health;

	@Mock
	private Dept44CompositeHealthContributor healthContributor;

	@Test
	void testSetHealthIndicatorHealthy() {
		// arrange
		final String methodName = "TestTask";
		dept44Health.setHealthIndicatorUnhealthy(methodName, "Initial error");
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());

		// act
		dept44Health.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testSetHealthIndicatorHealthyWhenAlreadyHealthy() {
		// arrange
		final String methodName = "TestTask";
		dept44Health.setHealthIndicatorHealthy(methodName);
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());

		// act
		dept44Health.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testSetHealthIndicatorHealthyForNonExistentMethod() {
		// arrange
		final String methodName = "NonExistentTask";

		// act
		dept44Health.setHealthIndicatorHealthy(methodName);

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName)).isNull();
	}

	@Test
	void testSetHealthIndicatorUnhealthy() {
		// arrange
		final String methodName = "TestTask";
		final String errorMessage = "Test error";
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());

		// act
		dept44Health.setHealthIndicatorUnhealthy(methodName, errorMessage);

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getDetails()).containsEntry("Reason", errorMessage);
	}

	@Test
	void testSetHealthIndicatorUnhealthyForNonExistentMethod() {
		// arrange
		final String methodName = "NonExistentTask";
		final String errorMessage = "Test error";

		// act
		dept44Health.setHealthIndicatorUnhealthy(methodName, errorMessage);

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName)).isNull();
	}

	@Test
	void testSetHealthIndicatorUnhealthyWhenAlreadyUnhealthy() {
		// arrange
		final String methodName = "TestTask";
		final String errorMessage = "Initial error";
		dept44Health.setHealthIndicatorUnhealthy(methodName, errorMessage);
		when(healthContributor.getOrCreateIndicator("TestTask")).thenReturn(new Dept44HealthIndicator());

		// act
		dept44Health.setHealthIndicatorUnhealthy(methodName, "Another error");

		// assert
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(healthContributor.getOrCreateIndicator(methodName).health().getDetails()).containsEntry("Reason", "Another error");
	}
}
