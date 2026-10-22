package se.sundsvall.dept44.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatNoException;

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
			"logbook.body-filters.allowlist.enabled=false",
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

	@Test
	void logbookDefaultMaskingAppliesWithoutConfiguredFilters() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class))
			.withPropertyValues("logbook.default.excluded.paths=/actuator/**", "logbook.logger.name=" + LOGGER_NAME, "logbook.body-filters.allowlist.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString("{\"access_token\":\"secret-token\"}"))
					.write();

				assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
					.contains("\"access_token\":\"XXX\"")
					.doesNotContain("secret-token"));
			});
	}

	/**
	 * The formatter is asked to validate the body, so a body that is not a JSON document is written as a quoted string
	 * rather than put into the entry as it is. Without that, a body whose first and last character could begin and end a
	 * document is embedded unchecked, and the entry it lands in is no longer parseable - which costs that entry its
	 * fields wherever the log server reads it as JSON.
	 * <p>
	 * Without any {@code logbook.body-filters} configured, which is where a service that masks its payload with a
	 * {@link org.zalando.logbook.BodyFilter} bean of its own ends up: nothing replaces an unparseable body on the way
	 * to the formatter, so whatever the mask wrote is what the formatter has to judge. The allowlist is disabled, since
	 * it replaces such a body with a note of its own.
	 */
	@ParameterizedTest
	@MethodSource("bodiesThatAreNotJsonDocuments")
	void aJsonBodyThatIsNoDocumentIsWrittenAsAStringSoTheEntryStaysParseable(final String body) {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperConfiguration.class, LogbookConfiguration.class))
			.withPropertyValues("logbook.default.excluded.paths=/actuator/**", "logbook.logger.name=" + LOGGER_NAME, "logbook.body-filters.allowlist.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();

				context.getBean(Logbook.class)
					.process(MockHttpRequest.create().withContentType("application/json").withBodyAsString(body))
					.write();

				assertThat(appender.list).singleElement().satisfies(event -> {
					final var entry = event.getFormattedMessage();
					assertThatNoException()
						.describedAs("the log entry has to be a JSON document: %s", entry)
						.isThrownBy(() -> new ObjectMapper().readTree(entry));
					assertThat(new ObjectMapper().readTree(entry).get("body").asText()).isEqualTo(body);
				});
			});
	}

	private static Stream<Arguments> bodiesThatAreNotJsonDocuments() {
		return Stream.of(
			// What a mask reads as: a character that could begin one and a character that could end one is all the
			// formatter asks for when it does not validate
			Arguments.of("[masked]"),
			Arguments.of("{masked}"),
			// What a body cut short reads as
			Arguments.of("{\"unclosed\":\"value\""),
			Arguments.of("[1, 2,"),
			Arguments.of("[]]"));
	}

	private static Stream<Arguments> bodiesLargerThanMaxBodySizeToLog() {
		return Stream.of(
			Arguments.of("application/json", "{\"secret\":\"top secret value\",\"padding\":\"" + PADDING + "\"}", "***"),
			Arguments.of("text/xml", "<a><secret>top secret value</secret><padding>" + PADDING + "</padding></a>", "<secret>***</secret>"));
	}
}
