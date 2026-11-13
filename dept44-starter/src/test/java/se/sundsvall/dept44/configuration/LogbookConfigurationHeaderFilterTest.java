package se.sundsvall.dept44.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.test.MockHttpRequest;
import org.zalando.logbook.test.MockHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Header filters as the Logbook built by {@link LogbookConfiguration} applies them, written to the payload log.
 */
class LogbookConfigurationHeaderFilterTest {

	private static final String LOGGER_NAME = "test.payload";

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
	void authorizationIsMaskedWithoutHeaderFilterBeans() {
		contextRunner.run(context -> {
			logRequest(context.getBean(Logbook.class));

			final var headers = requestHeaders();
			assertThat(headers.get("Authorization").get(0).asString()).isEqualTo("XXX");
			assertThat(headers.get("x-secret").get(0).asString()).isEqualTo("secret-value");
		});
	}

	@Test
	void headerFilterBeansAreAppliedAndAuthorizationStaysMasked() {
		contextRunner
			.withBean("secretHeaderFilter", HeaderFilter.class, () -> HeaderFilters.replaceHeaders("x-secret", "XXX"))
			.run(context -> {
				logRequest(context.getBean(Logbook.class));

				final var headers = requestHeaders();
				assertThat(headers.get("Authorization").get(0).asString()).isEqualTo("XXX");
				assertThat(headers.get("x-secret").get(0).asString()).isEqualTo("XXX");
				assertThat(headers.get("x-request-id").get(0).asString()).isEqualTo("request-id");
				assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain("secret-value", "Bearer token"));
			});
	}

	private static void logRequest(final Logbook logbook) throws Exception {
		logbook.process(MockHttpRequest.create()
			.withPath("/things")
			.withHeaders(HttpHeaders.empty()
				.update("Authorization", "Bearer token")
				.update("x-secret", "secret-value")
				.update("x-request-id", "request-id")))
			.write()
			.process(MockHttpResponse.create().withStatus(200))
			.write();
	}

	private JsonNode requestHeaders() {
		return jsonMapper.readTree(appender.list.getFirst().getFormattedMessage()).get("headers");
	}
}
