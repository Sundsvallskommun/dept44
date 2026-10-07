package se.sundsvall.dept44.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.test.MockHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Body filters as the Logbook built by {@link LogbookConfiguration} applies them, written to the payload log.
 */
class LogbookConfigurationBodyFilterTest {

	private static final String LOGGER_NAME = "test.payload";
	private static final String PADDING = "x".repeat(100);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class))
		.withPropertyValues(
			"logbook.default.excluded.paths=/actuator/**",
			"logbook.logger.name=" + LOGGER_NAME,
			"logbook.logs.maxBodySizeToLog=100",
			"logbook.body-filters.json-path[0].key=$..secret",
			"logbook.body-filters.json-path[0].value=***",
			"logbook.body-filters.x-path[0].key=//secret/text()",
			"logbook.body-filters.x-path[0].value=***");

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

	/**
	 * A body cut short is no longer valid JSON or XML, so it must be masked before it is truncated.
	 */
	@ParameterizedTest
	@MethodSource("bodiesLargerThanMaxBodySizeToLog")
	void bodyLargerThanMaxBodySizeToLogIsMaskedBeforeItIsTruncated(final String contentType, final String body, final String masked) {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();

			context.getBean(Logbook.class)
				.process(MockHttpRequest.create().withContentType(contentType).withBodyAsString(body))
				.write();

			assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
				.contains(masked)
				.doesNotContain("top secret value"));
		});
	}

	private static Stream<Arguments> bodiesLargerThanMaxBodySizeToLog() {
		return Stream.of(
			Arguments.of("application/json", "{\"secret\":\"top secret value\",\"padding\":\"" + PADDING + "\"}", "***"),
			Arguments.of("text/xml", "<a><secret>top secret value</secret><padding>" + PADDING + "</padding></a>", "<secret>***</secret>"));
	}
}
