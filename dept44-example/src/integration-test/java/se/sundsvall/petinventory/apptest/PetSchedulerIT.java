package se.sundsvall.petinventory.apptest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.siegmar.logbackgelf.GelfEncoder;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
	"scheduler.pet-scheduler.cron=* * * * * *", // Setup to execute every second
	"server.shutdown=immediate",
	"spring.lifecycle.timeout-per-shutdown-phase=0s",
	"wiremock.server.port=8089" // Provide default port to satisfy URL validation
})
@ActiveProfiles("it")
class PetSchedulerIT {

	final ListAppender<ILoggingEvent> petSchedulerAppender = new MdcCapturingListAppender();
	final ListAppender<ILoggingEvent> aspectAppender = new MdcCapturingListAppender();
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

		// Verify structured MDC fields land on the aspect's "done" log line
		final var doneEvent = aspectAppender.list.stream()
			.filter(event -> event.getFormattedMessage().contains("done"))
			.findFirst()
			.orElseThrow();
		final var mdc = doneEvent.getMDCPropertyMap();
		assertThat(mdc).containsEntry("schedulerName", "pet-scheduler");
		assertThat(mdc).containsEntry("outcome", "SUCCESS");
		assertThat(mdc.get("executionId")).isNotBlank();
		assertThat(Long.parseLong(mdc.get("durationMs"))).isGreaterThanOrEqualTo(0L);

		// Verify the same fields can be picked out by the JSON (GELF) encoder used in production
		final var encoder = new GelfEncoder();
		encoder.setContext(((Logger) LoggerFactory.getLogger(Dept44SchedulerAspect.class)).getLoggerContext());
		encoder.setIncludeMdcData(true);
		encoder.start();
		try {
			final var json = new String(encoder.encode(doneEvent), StandardCharsets.UTF_8);
			final var node = JsonMapper.builder().build().readTree(json);
			// GELF prefixes additional (MDC) fields with an underscore
			assertThat(node.get("_schedulerName").asString()).isEqualTo("pet-scheduler");
			assertThat(node.get("_outcome").asString()).isEqualTo("SUCCESS");
			assertThat(node.get("_executionId").asString()).isNotBlank();
			assertThat(node.get("_durationMs")).isNotNull();
			assertThat(Long.parseLong(node.get("_durationMs").asString())).isGreaterThanOrEqualTo(0L);
		} finally {
			encoder.stop();
		}
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

		// Verify the failure outcome lands in MDC
		final var failEvent = aspectAppender.list.stream()
			.filter(event -> event.getFormattedMessage().contains("fail"))
			.findFirst()
			.orElseThrow();
		assertThat(failEvent.getMDCPropertyMap()).containsEntry("schedulerName", "pet-scheduler");
		assertThat(failEvent.getMDCPropertyMap()).containsEntry("outcome", "FAILURE");
	}

	@TestConfiguration
	public static class ShedlockTestConfiguration {

		@Bean
		@Primary
		PetSchedulerWorker createMock() {
			return Mockito.mock(PetSchedulerWorker.class);
		}
	}

	/**
	 * {@link ListAppender} that eagerly snapshots the MDC of each event as it is appended, so the MDC populated by the
	 * aspect on the scheduler thread can be asserted from the test thread after the run has completed.
	 */
	private static class MdcCapturingListAppender extends ListAppender<ILoggingEvent> {
		@Override
		protected void append(final ILoggingEvent eventObject) {
			eventObject.getMDCPropertyMap();
			super.append(eventObject);
		}
	}
}
