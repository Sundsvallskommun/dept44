package se.sundsvall.dept44.scheduling;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Dept44SchedulerAspectMaximumExecutionTimeTest {

	@Test
	void acceptsIsoAndSimpleDurations() {
		assertThat(Dept44SchedulerAspect.maximumExecutionTime("TestTask", "PT5M")).isEqualTo(Duration.ofMinutes(5));
		assertThat(Dept44SchedulerAspect.maximumExecutionTime("TestTask", "5m")).isEqualTo(Duration.ofMinutes(5));
		assertThat(Dept44SchedulerAspect.maximumExecutionTime("TestTask", "300s")).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void invalidValueFallsBackToTheDefault() {
		assertThat(Dept44SchedulerAspect.maximumExecutionTime("TestTask", "${unresolved}")).isEqualTo(Duration.ofMinutes(2));
	}
}
