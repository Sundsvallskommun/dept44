package se.sundsvall.dept44.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.test.MockHttpRequest;
import org.zalando.logbook.test.MockHttpResponse;
import se.sundsvall.dept44.logbook.filter.BodyFilterProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The allowlist as the Logbook built by {@link LogbookConfiguration} applies it, written to the payload log.
 */
class LogbookConfigurationAllowlistTest {

	private static final String LOGGER_NAME = "test.payload";
	private static final String PERSONAL_NUMBER = "190101011234";
	private static final String BODY = "{\"id\":\"case-1\",\"personalNumber\":\"" + PERSONAL_NUMBER + "\"}";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class))
		.withPropertyValues("logbook.default.excluded.paths=/actuator/**", "logbook.logger.name=" + LOGGER_NAME);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
	private Logger payloadLogger;

	@BeforeEach
	void captureLog() {
		payloadLogger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
		payloadLogger.setLevel(Level.TRACE);
		appender.start();
		payloadLogger.addAppender(appender);
	}

	@AfterEach
	void releaseLog() {
		payloadLogger.detachAppender(appender);
		payloadLogger.setLevel(null);
	}

	@Test
	void everyValueIsMaskedByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();

			context.getBean(Logbook.class)
				.process(MockHttpRequest.create().withMethod("POST").withPath("/cases").withContentType("application/json").withBodyAsString(BODY))
				.write()
				.process(MockHttpResponse.create().withStatus(201).withHeaders(HttpHeaders.of("x-request-id", "request-id")).withContentType("application/json").withBodyAsString(BODY))
				.write();

			final var entries = logEntries();
			assertThat(entries).hasSize(2).allSatisfy(entry -> assertThat(entry.get("body").toString())
				.isEqualTo("{\"id\":\"[masked]\",\"personalNumber\":\"[masked]\"}"));
			assertThat(entries.getFirst().get("method").asString()).isEqualTo("POST");
			assertThat(entries.getFirst().get("uri").asString()).endsWith("/cases");
			assertThat(entries.getLast().get("status").asInt()).isEqualTo(201);
			assertThat(entries.getLast().get("headers").has("x-request-id")).isTrue();
			assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(PERSONAL_NUMBER));
		});
	}

	@Test
	void valuesOfTheListedFieldsAreLogged() {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.paths[0]=$..id")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(BODY))
					.write();

				assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.get("body").toString())
					.isEqualTo("{\"id\":\"case-1\",\"personalNumber\":\"[masked]\"}"));
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"false", "off"
	})
	void bodiesAreLoggedInFullWhenTheAllowlistIsDisabled(final String disabled) {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.enabled=" + disabled)
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(BODY))
					.write();

				assertThat(logEntries()).singleElement()
					.satisfies(entry -> assertThat(entry.get("body").get("personalNumber").asString()).isEqualTo(PERSONAL_NUMBER));
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"text/plain", "application/xml", "application/x-www-form-urlencoded"
	})
	void bodyThatIsNotJsonIsLoggedWithoutBodyByDefault(final String contentType) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();

			context.getBean(Logbook.class)
				.process(MockHttpRequest.create().withContentType(contentType).withBodyAsString("<a>" + PERSONAL_NUMBER + "</a>"))
				.write();

			assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.has("body")).isFalse());
		});
	}

	@Test
	void allowlistAppliesToWhatOtherBodyFiltersLeave() {
		contextRunner
			.withBean(BodyFilter.class, () -> (contentType, body) -> "{\"added\":\"by a filter\"}")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(BODY))
					.write();

				assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.get("body").toString())
					.isEqualTo("{\"added\":\"[masked]\"}"));
			});
	}

	/**
	 * A body cut short is no longer valid JSON, so it must be masked before it is truncated. Masked after, it would be
	 * replaced by a note, and the kept id would be gone.
	 */
	@Test
	void bodyIsMaskedBeforeItIsTruncated() {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.paths[0]=$..id", "logbook.logs.maxBodySizeToLog=20")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json")
						.withBodyAsString("{\"id\":\"case-1\",\"description\":\"" + "x".repeat(100) + "\"}"))
					.write();

				assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
					.contains("case-1", "...")
					.doesNotContain("xxxx", "bodyOmitted"));
			});
	}

	@Test
	void noteForABodyLargerThanWasCapturedIsKept() {
		contextRunner
			.withPropertyValues("logbook.logs.maxBodySizeToCapture=10B")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(BODY))
					.write();

				assertThat(logEntries()).singleElement()
					.satisfies(entry -> assertThat(entry.get("body").get("bodyOmitted").asString()).isEqualTo("larger than 10 bytes"));
			});
	}

	@Test
	void maxSizeIsConfigurable() {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.max-size=10B")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(BODY))
					.write();

				assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.get("body").get("bodyOmitted").asString())
					.isEqualTo("larger than 10 characters, so the allowlist could not be applied"));
			});
	}

	@Test
	void emptyMaxSizeKeepsTheDefault() {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.max-size=", "logbook.body-filters.allowlist.paths[0]=$..id")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json")
						.withBodyAsString("{\"id\":\"case-1\",\"description\":\"" + "x".repeat(300_000) + "\"}"))
					.write();

				assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.get("body").get("bodyOmitted").asString())
					.isEqualTo("larger than 262144 characters, so the allowlist could not be applied"));
			});
	}

	@Test
	void negativeMaxSizeDisablesTheLimit() {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.max-size=-1", "logbook.body-filters.allowlist.paths[0]=$..id")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json")
						.withBodyAsString("{\"id\":\"case-1\",\"description\":\"" + "x".repeat(300_000) + "\"}"))
					.write();

				assertThat(logEntries()).singleElement().satisfies(entry -> assertThat(entry.get("body").toString())
					.isEqualTo("{\"id\":\"case-1\",\"description\":\"[masked]\"}"));
			});
	}

	@ParameterizedTest
	@ValueSource(booleans = {
		true, false
	})
	void invalidPathStopsStartupWhetherTheAllowlistIsEnabledOrNot(final boolean enabled) {
		contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.enabled=" + enabled, "logbook.body-filters.allowlist.paths[0]=title")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("Invalid path 'title' in logbook.body-filters.allowlist.paths");
			});
	}

	/**
	 * Observed through the note the xpath filters log for a body that is not valid XML.
	 */
	@ParameterizedTest
	@ValueSource(booleans = {
		true, false
	})
	void xPathFiltersOnlyRunWhenTheAllowlistIsDisabled(final boolean enabled) {
		final var providerLogger = (Logger) LoggerFactory.getLogger(BodyFilterProvider.class);
		final var providerAppender = new ListAppender<ILoggingEvent>();
		providerAppender.start();
		providerLogger.addAppender(providerAppender);
		providerLogger.setLevel(Level.DEBUG);

		try {
			contextRunner
				.withPropertyValues("logbook.body-filters.allowlist.enabled=" + enabled, "logbook.body-filters.x-path[0].key=//secret/text()",
					"logbook.body-filters.x-path[0].value=***")
				.run(context -> {
					assertThat(context).hasNotFailed();

					context.getBean(Logbook.class)
						.process(MockHttpRequest.create().withContentType("text/xml").withBodyAsString("<not-closed>"))
						.write();
				});
		} finally {
			providerLogger.detachAppender(providerAppender);
			providerLogger.setLevel(null);
		}

		assertThat(providerAppender.list).hasSize(enabled ? 0 : 1);
	}

	@Test
	void invalidXPathStopsStartupAlsoWhenTheAllowlistIsEnabled() {
		contextRunner
			.withPropertyValues("logbook.body-filters.x-path[0].key=count(//secret)", "logbook.body-filters.x-path[0].value=***")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("Invalid xpath 'count(//secret)' in logbook.body-filters.x-path");
			});
	}

	@Test
	void whatIsDoneWithBodiesIsLoggedAtStartup() {
		final var events = configurationLog(() -> contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.paths[0]=$..id", "logbook.body-filters.allowlist.paths[1]=$..status")
			.run(context -> assertThat(context).hasNotFailed()));

		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.INFO);
			assertThat(event.getFormattedMessage()).startsWith("Payload log bodies are masked except for the values of the 2 field(s)");
		});
	}

	@Test
	void bodiesLoggedInFullAreLoggedAtStartup() {
		final var events = configurationLog(() -> contextRunner
			.withPropertyValues("logbook.body-filters.allowlist.enabled=false", "logbook.body-filters.x-path[0].key=//secret/text()",
				"logbook.body-filters.x-path[0].value=***")
			.run(context -> assertThat(context).hasNotFailed()));

		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.INFO);
			assertThat(event.getFormattedMessage()).startsWith("Payload log bodies are logged in full");
		});
	}

	@Test
	void xPathFiltersThatAreNotAppliedAreWarnedAboutAtStartup() {
		final var events = configurationLog(() -> contextRunner
			.withPropertyValues("logbook.body-filters.x-path[0].key=//secret/text()", "logbook.body-filters.x-path[0].value=***")
			.run(context -> assertThat(context).hasNotFailed()));

		assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.WARN);
		assertThat(events.getLast().getFormattedMessage()).startsWith("logbook.body-filters.x-path is not applied");
	}

	private static List<ILoggingEvent> configurationLog(final Runnable run) {
		final var logger = (Logger) LoggerFactory.getLogger(LogbookConfiguration.class);
		final var events = new ListAppender<ILoggingEvent>();
		events.start();
		logger.addAppender(events);
		logger.setLevel(Level.INFO);
		try {
			run.run();
		} finally {
			logger.detachAppender(events);
			logger.setLevel(null);
		}
		return events.list;
	}

	private List<JsonNode> logEntries() {
		return appender.list.stream()
			.map(event -> jsonMapper.readTree(event.getFormattedMessage()))
			.toList();
	}
}
