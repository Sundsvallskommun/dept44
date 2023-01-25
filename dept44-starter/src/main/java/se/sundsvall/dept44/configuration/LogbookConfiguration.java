package se.sundsvall.dept44.configuration;

import static org.zalando.logbook.Conditions.exclude;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildJsonPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildXPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.passwordFilter;
import static se.sundsvall.dept44.util.EncodingUtils.fixDoubleEncodedUTF8Content;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.Conditions;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import org.zalando.logbook.json.JsonHttpLogFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureBefore(LogbookAutoConfiguration.class)
public class LogbookConfiguration {

	private final String loggerName;
	private final Set<String> excludedPaths;
	private final Map<String, String> exclusionFiltersJsonPath;
	private final Map<String, String> exclusionFiltersXPath;

	LogbookConfiguration(
		@Value("#{${logbook.exclusionfilters.json-path: {} }}") Map<String, String> exclusionFiltersJsonPath,
		@Value("#{${logbook.exclusionfilters.x-path: {} }}") Map<String, String> exclusionFiltersXPath,
		@Value("#{'${logbook.logger.name:${logbook.default.logger.name:}}'}") final String loggerName,
		@Value("#{'${logbook.excluded.paths:${logbook.default.excluded.paths:}}'}") final Set<String> excludedPaths) {
		this.loggerName = loggerName;
		this.excludedPaths = excludedPaths;
		this.exclusionFiltersJsonPath = Objects.requireNonNullElseGet(exclusionFiltersJsonPath, Map::of);
		this.exclusionFiltersXPath = Objects.requireNonNullElseGet(exclusionFiltersXPath, Map::of);
	}

	@Bean
	@ConditionalOnMissingBean
	Logbook logbook(final ObjectMapper objectMapper, final List<BodyFilter> bodyFilters) {
		return Logbook.builder()
			.sink(new DefaultSink(
				new JsonHttpLogFormatter(objectMapper),
				new NamedLoggerHttpLogWriter(loggerName)))
			.bodyFilter(passwordFilter())
			.bodyFilters(buildJsonPathFilters(exclusionFiltersJsonPath))
			.bodyFilters(buildXPathFilters(exclusionFiltersXPath))
			.bodyFilters(Optional.ofNullable(bodyFilters).orElse(List.of()))
			.condition(exclude(getExclusions()))
			.build();
	}

	private List<Predicate<HttpRequest>> getExclusions() {
		return Optional.of(excludedPaths).stream()
			.flatMap(Set::stream)
			.map(Conditions::requestTo)
			.toList();
	}

	/**
	 * Custom HttpLogWriter to allow for Logbook logs being written to a named logger.
	 */
	static class NamedLoggerHttpLogWriter implements HttpLogWriter {

		private final Logger log;

		NamedLoggerHttpLogWriter(final String name) {
			log = LoggerFactory.getLogger(name);
		}

		@Override
		public boolean isActive() {
			return log.isTraceEnabled();
		}

		@Override
		public void write(final Precorrelation precorrelation, final String request) {
			log.trace(request);
		}

		@Override
		public void write(final Correlation correlation, final String response) {
			final var logMessage = fixDoubleEncodedUTF8Content(response);
			log.trace(logMessage);
		}
	}
}
