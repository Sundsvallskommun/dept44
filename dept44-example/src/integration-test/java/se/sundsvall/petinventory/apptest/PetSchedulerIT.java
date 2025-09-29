package se.sundsvall.petinventory.apptest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.dept44.scheduling.Dept44SchedulerAspect;
import se.sundsvall.petinventory.service.scheduler.PetScheduler;
import se.sundsvall.petinventory.service.scheduler.PetSchedulerWorker;

@SpringBootTest(properties = {
	"scheduler.pet-scheduler.cron=* * * * * *", // Setup to execute every second
	"server.shutdown=immediate",
	"spring.lifecycle.timeout-per-shutdown-phase=0s"
})
@ActiveProfiles("it")
class PetSchedulerIT {

	final ListAppender<ILoggingEvent> petSchedulerAppender = new ListAppender<>();
	final ListAppender<ILoggingEvent> aspectAppender = new ListAppender<>();
	@Autowired
	private PetSchedulerWorker petSchedulerWorkerMock;

	@BeforeEach
	void setup() {
		// get Logback Logger
		final var petSchedulerLogger = (Logger) LoggerFactory.getLogger(PetScheduler.class);
		petSchedulerAppender.start();
		petSchedulerLogger.addAppender(petSchedulerAppender);

		final var aspectLogger = (Logger) LoggerFactory.getLogger(Dept44SchedulerAspect.class);
		aspectAppender.start();
		aspectLogger.addAppender(aspectAppender);
	}

	@Test
	@DirtiesContext
	void testScheduledJobRunsAutomatically() {

		// Wait for the scheduled job to run
		await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
			// Verify that the scheduled job has run
			verify(petSchedulerWorkerMock).getPets();

		});

		// Verify the log messages inside the scheduled job
		assertThat(petSchedulerAppender.list).hasSize(2)
			.extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly(
				"Getting and processing pets",
				"Finished getting and processing pets"
			);

		// Verify the log messages from the aspect
		assertThat(aspectAppender.list).hasSize(2)
			.extracting(ILoggingEvent::getFormattedMessage)
			.anyMatch(message -> message.matches("Scheduled method pet-scheduler start\\. RequestID=.*"))
			.anyMatch(message -> message.matches("Scheduled method pet-scheduler done\\. RequestID=.*"));
	}

	@Test
	@DirtiesContext
	void testScheduledJobRunsAutomaticallyThrowException() {
		when(petSchedulerWorkerMock.getPets()).thenThrow(new RuntimeException("Test exception"));

		// Wait for the scheduled job to run
		await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
			// Verify that the scheduled job has run
			verify(petSchedulerWorkerMock).getPets();

		});

		// Verify the log messages inside the scheduled job
		assertThat(petSchedulerAppender.list).hasSize(1)
			.extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly("Getting and processing pets");

		// Verify the log messages from the aspect
		assertThat(aspectAppender.list).hasSize(2)
			.extracting(ILoggingEvent::getFormattedMessage)
			.anyMatch(message -> message.matches("Scheduled method pet-scheduler start\\. RequestID=.*"))
			.anyMatch(message -> message.matches("Scheduled method pet-scheduler fail\\. RequestID=.*"));
	}

	@TestConfiguration
	public static class ShedlockTestConfiguration {

		@Bean
		@Primary
		PetSchedulerWorker createMock() {
			return Mockito.mock(PetSchedulerWorker.class);
		}
	}
}
