package se.sundsvall.dept44.scheduling.health;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Dept44HealthIndicatorTest {

	private Dept44HealthIndicator healthIndicator;

	@BeforeEach
	void setUp() {
		healthIndicator = new Dept44HealthIndicator();
	}

	@Test
	void testHealthInitiallyUp() {
		// act
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testSetUnhealthy() {
		// act
		healthIndicator.setUnhealthy();
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(health.getDetails()).containsEntry("Reason", "Unknown");
	}

	@Test
	void testSetUnhealthyWithReason() {
		// arrange
		final var reason = "Test reason";
		// act
		healthIndicator.setUnhealthy(reason);
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(health.getDetails()).containsEntry("Reason", reason);
	}

	@Test
	void testSetHealthy() {
		// arrange
		healthIndicator.setUnhealthy();
		// act
		healthIndicator.setHealthy();
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testResetErrors() {
		// arrange
		healthIndicator.setUnhealthy();
		// act
		healthIndicator.resetErrors();
		// assert
		assertThat(healthIndicator.hasErrors()).isFalse();
	}

	@Test
	void testHasErrors() {
		// act & assert
		assertThat(healthIndicator.hasErrors()).isFalse();
		healthIndicator.setUnhealthy();
		assertThat(healthIndicator.hasErrors()).isTrue();
	}

	@Test
	void testSetUnhealthyWithNullReason() {
		// act
		healthIndicator.setUnhealthy(null);
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(health.getDetails()).containsEntry("Reason", "Unknown");
	}

	@Test
	void testSetHealthyAfterUnhealthyWithReason() {
		// arrange
		final var reason = "Test reason";
		healthIndicator.setUnhealthy(reason);
		// act
		healthIndicator.setHealthy();
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("UP");
		assertThat(health.getDetails()).doesNotContainKey("Reason");
	}

	@Test
	void testSetUnhealthyMultipleTimes() {
		// act
		healthIndicator.setUnhealthy("First reason");
		healthIndicator.setUnhealthy("Second reason");
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat(health.getDetails()).size().isEqualTo(1);
		assertThat(health.getDetails()).containsEntry("Reason", "Second reason");
	}

	@Test
	void testSetHealthyWithoutSettingUnhealthy() {
		// act
		healthIndicator.setHealthy();
		final var health = healthIndicator.health();
		// assert
		assertThat(health.getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testRunStillGoingOnAfterItsMaximumExecutionTimeIsRestricted() {
		healthIndicator.runStarted(Duration.ofMillis(-1));

		final var health = healthIndicator.health();
		assertThat(health.getStatus().getCode()).isEqualTo("RESTRICTED");
		assertThat((String) health.getDetails().get("Reason")).startsWith("Maximum execution time exceeded, still running since");

		healthIndicator.runFinished();

		assertThat(healthIndicator.health().getStatus().getCode()).isEqualTo("UP");
	}

	@Test
	void testRunWithinItsMaximumExecutionTimeIsUp() {
		healthIndicator.runStarted(Duration.ofMinutes(2));

		assertThat(healthIndicator.health().getStatus().getCode()).isEqualTo("UP");
	}
}
