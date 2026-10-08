package se.sundsvall.dept44.scheduling;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class Dept44ScheduledTest {

	@Test
	void lockAtLeastForIsPassedToShedLock() throws Exception {
		final var method = Dept44ScheduledTest.class.getDeclaredMethod("scheduled");

		final var schedulerLock = AnnotatedElementUtils.findMergedAnnotation(method, SchedulerLock.class);

		assertThat(schedulerLock).isNotNull();
		assertThat(schedulerLock.name()).isEqualTo("Job");
		assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT10M");
		assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("PT30S");
	}

	@Dept44Scheduled(cron = "0 0 * * * *", name = "Job", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
	void scheduled() {
		// Annotated for the test above
	}
}
