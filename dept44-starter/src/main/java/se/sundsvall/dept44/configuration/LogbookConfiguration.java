package se.sundsvall.dept44.configuration;

import static org.zalando.logbook.core.Conditions.exclude;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildJsonPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildXPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.passwordFilter;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.fileAttachmentFilter;
import static se.sundsvall.dept44.util.EncodingUtils.fixDoubleEncodedUTF8Content;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import org.zalando.logbook.core.Conditions;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.json.JsonHttpLogFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureBefore(LogbookAutoConfiguration.class)
@EnableConfigurationProperties({ ExclusionFilterProperties.class, BodyFilterProperties.class })
public class LogbookConfiguration {

	private final String loggerName;
	private final Set<String> excludedPaths;

	LogbookConfiguration(
		@Value("#{'${logbook.logger.name:${logbook.default.logger.name:}}'}") String loggerName,
		@Value("${logbook.default.excluded.paths}") Set<String> defaultExcludedPaths,
		@Value("${logbook.excluded.paths:}") Set<String> additionalExcludedPaths) {
		this.loggerName = loggerName;

		excludedPaths = Stream.of(defaultExcludedPaths, additionalExcludedPaths)
			.flatMap(Collection::stream)
			.collect(Collectors.toSet());
	}

	@Bean
	@ConditionalOnMissingBean
	Logbook logbook(final ObjectMapper objectMapper, final List<BodyFilter> bodyFilters, ExclusionFilterProperties exclusionFilterProperties, BodyFilterProperties bodyFilterProperties) {
		return Logbook.builder()
			.sink(new DefaultSink(
				new JsonHttpLogFormatter(objectMapper),
				new NamedLoggerHttpLogWriter(loggerName)))
			.responseFilter(fileAttachmentFilter())
			.bodyFilter(passwordFilter())
			// Old deprecated way
			.bodyFilters(buildJsonPathFilters(objectMapper, Objects.requireNonNullElseGet(exclusionFilterProperties.getJsonPath(), Map::of)))
			.bodyFilters(buildXPathFilters(Objects.requireNonNullElseGet(exclusionFilterProperties.getXPath(), Map::of)))
			// New way
			.bodyFilters(buildJsonPathFilters(objectMapper,
				Optional.ofNullable(bodyFilterProperties.getJsonPath())
					.orElseGet(Collections::emptyList)
					.stream()
					.reduce(new HashMap<>(), (acc, map) -> {
						acc.put(map.get("key"), map.get("value"));
						return acc;
					})))
			.bodyFilters(buildXPathFilters(
				Optional.ofNullable(bodyFilterProperties.getxPath())
					.orElseGet(Collections::emptyList)
					.stream()
					.reduce(new HashMap<>(), (acc, map) -> {
						acc.put(map.get("key"), map.get("value"));
						return acc;
					})))
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
