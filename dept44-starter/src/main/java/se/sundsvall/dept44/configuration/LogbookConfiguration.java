package se.sundsvall.dept44.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.unit.DataSize;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.LogbookCreator;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import org.zalando.logbook.core.BodyFilters;
import org.zalando.logbook.core.Conditions;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.json.JsonHttpLogFormatter;
import org.zalando.logbook.servlet.AsyncOnCompleteListenerWrapper;
import org.zalando.logbook.servlet.LogbookFilter;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;
import se.sundsvall.dept44.logbook.filter.AllowlistBodyFilter;
import se.sundsvall.dept44.logbook.servlet.LogbookServletFilter;
import tools.jackson.databind.json.JsonMapper;

import static jakarta.servlet.DispatcherType.ASYNC;
import static jakarta.servlet.DispatcherType.REQUEST;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;
import static org.zalando.logbook.core.Conditions.exclude;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildJsonPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.buildXPathFilters;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.oversizedBodyFilter;
import static se.sundsvall.dept44.logbook.filter.BodyFilterProvider.passwordFilter;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.binaryContentFilter;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.fileAttachmentFilter;
import static se.sundsvall.dept44.util.EncodingUtils.fixDoubleEncodedUTF8Content;

@Configuration
@AutoConfigureBefore(LogbookAutoConfiguration.class)
@EnableConfigurationProperties({
	BodyFilterProperties.class
})
public class LogbookConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogbookConfiguration.class);

	private final String loggerName;
	private final Set<String> excludedPaths;
	private final int maxBodySizeToLog;
	private final long maxBodySizeToCapture;

	/**
	 * Constructor for LogbookConfiguration.
	 *
	 * @param loggerName              The name of the logger to use.
	 * @param defaultExcludedPaths    The default paths to exclude from logging.
	 * @param additionalExcludedPaths Additional paths to exclude from logging.
	 * @param maxBodySizeToLog        The maximum size of the body to log. A larger body is cut after the body filters have
	 *                                masked it. Defaults to -1 (disabled).
	 * @param maxBodySizeToCapture    The largest body that payload logging may hold in memory. Larger bodies, and binary
	 *                                or attachment bodies of any size, are logged without their body and never copied
	 *                                into memory. Defaults to 1MB, -1 disables the limit.
	 */
	LogbookConfiguration(
		@Value("#{'${logbook.logger.name:${logbook.default.logger.name:}}'}") final String loggerName,
		@Value("${logbook.default.excluded.paths}") final Set<String> defaultExcludedPaths,
		@Value("${logbook.excluded.paths:}") final Set<String> additionalExcludedPaths,
		@Value("${logbook.logs.maxBodySizeToLog:-1}") final int maxBodySizeToLog,
		@Value("${logbook.logs.maxBodySizeToCapture:1MB}") final String maxBodySizeToCapture) {

		this.maxBodySizeToLog = maxBodySizeToLog;
		this.maxBodySizeToCapture = DataSize.parse(maxBodySizeToCapture).toBytes();
		this.loggerName = loggerName;
		excludedPaths = Stream.of(defaultExcludedPaths, additionalExcludedPaths)
			.flatMap(Collection::stream)
			.collect(Collectors.toSet());
	}

	@Bean
	@ConditionalOnMissingBean
	BodyCapturePolicy bodyCapturePolicy() {
		return new BodyCapturePolicy(maxBodySizeToCapture);
	}

	@Bean
	@ConditionalOnMissingBean
	Logbook logbook(final JsonMapper jsonMapper,
		final ObjectMapper objectMapper, final List<BodyFilter> bodyFilters, final BodyFilterProperties bodyFilterProperties,
		final BodyCapturePolicy bodyCapturePolicy) {
		final var builder = Logbook.builder()
			.strategy(new BodyCaptureStrategy(bodyCapturePolicy))
			.bodyFilter(oversizedBodyFilter(bodyCapturePolicy));

		builder.sink(new DefaultSink(
			// Asked to validate the body, so that one that is not JSON is written as a quoted string rather than put
			// into the entry as it is. Without it the formatter asks only whether the first and last character could
			// begin and end a document, which a masked, truncated or replaced body can pass while being no document at
			// all - and the entry it lands in is then unparseable, which costs that entry its fields wherever the log
			// server reads it as JSON. The cost is one streaming parse per JSON body.
			new JsonHttpLogFormatter(jsonMapper, true),
			new NamedLoggerHttpLogWriter(loggerName)))
			.responseFilters(List.of(
				fileAttachmentFilter(),
				binaryContentFilter()))
			.bodyFilter(passwordFilter())
			// Logbook's own masking of tokens and credentials, which it drops as soon as any body filter is configured
			.bodyFilter(BodyFilters.defaultValue());

		// Built even when they are not used, so that an invalid xpath or allowlist path still stops startup
		final var xPathFilters = buildXPathFilters(
			Optional.ofNullable(bodyFilterProperties.getxPath())
				.orElseGet(Collections::emptyList)
				.stream()
				.reduce(new HashMap<>(), (acc, map) -> {
					acc.put(map.get("key"), map.get("value"));
					return acc;
				}));
		final var allowlist = bodyFilterProperties.getAllowlist();
		final var allowlistFilter = new AllowlistBodyFilter(allowlist.getPaths(), allowlist.getMaxSize().toBytes());

		builder.bodyFilters(buildJsonPathFilters(objectMapper, Optional.ofNullable(bodyFilterProperties.getJsonPath())
			.orElseGet(Collections::emptyList)
			.stream()
			.reduce(new HashMap<>(), (acc, map) -> {
				acc.put(map.get("key"), map.get("value"));
				return acc;
			})));
		if (!allowlist.isEnabled()) {
			// With the allowlist enabled, an XML body is logged without its body, so these would only cost a parse
			builder.bodyFilters(xPathFilters);
		}
		builder.bodyFilters(Optional.ofNullable(bodyFilters).orElse(List.of()));

		if (allowlist.isEnabled()) {
			// After every other body filter, so that only what the allowlist names is logged, whatever they leave
			builder.bodyFilter(allowlistFilter);
		}
		logAllowlist(allowlist, xPathFilters);

		// Truncated last: a body cut short is no longer valid JSON or XML, so the filters above could not mask it
		setMaxBodySizeToLog(builder);

		return builder
			.condition(exclude(getExclusions()))
			.build();
	}

	/**
	 * Says once, at startup, what the payload log does with bodies, since a service that upgrades into the allowlist sees
	 * its log change without having changed anything itself.
	 */
	private static void logAllowlist(final BodyFilterProperties.Allowlist allowlist, final List<BodyFilter> xPathFilters) {
		if (!allowlist.isEnabled()) {
			LOGGER.info("Payload log bodies are logged in full, since logbook.body-filters.allowlist.enabled is false");
			return;
		}
		LOGGER.info("Payload log bodies are masked except for the values of the {} field(s) in logbook.body-filters.allowlist.paths. "
			+ "A body that is not JSON is logged without its body", allowlist.getPaths().size());
		if (!xPathFilters.isEmpty()) {
			LOGGER.warn("logbook.body-filters.x-path is not applied while logbook.body-filters.allowlist is enabled, since a body that is "
				+ "not JSON is then logged without its body");
		}
	}

	private void setMaxBodySizeToLog(final LogbookCreator.Builder builder) {
		if (maxBodySizeToLog > 0) {
			builder.bodyFilter(BodyFilters.truncate(maxBodySizeToLog));
		}
	}

	private List<Predicate<HttpRequest>> getExclusions() {
		return Optional.of(excludedPaths).stream()
			.flatMap(Set::stream)
			.map(Conditions::requestTo)
			.toList();
	}

	/**
	 * Replaces the servlet filters that Logbook would otherwise register, so that payload logging never holds more of a
	 * body in memory than {@code logbook.logs.maxBodySizeToCapture}. The bean names and enabling properties are Logbook's
	 * own, so {@code logbook.filter.enabled} and {@code logbook.secure-filter.enabled} work as before. Logbook's form
	 * request mode does not apply: form and multipart request bodies are never captured.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = SERVLET)
	@ConditionalOnClass(LogbookFilter.class)
	static class ServletFilterConfiguration {

		private static final String FILTER_NAME = "logbookFilter";
		private static final String SECURE_FILTER_NAME = "secureLogbookFilter";

		@Bean(FILTER_NAME)
		@ConditionalOnProperty(name = "logbook.filter.enabled", havingValue = "true", matchIfMissing = true)
		@ConditionalOnMissingBean(name = FILTER_NAME)
		FilterRegistrationBean<LogbookServletFilter> logbookFilter(final Logbook logbook, final BodyCapturePolicy bodyCapturePolicy,
			final ObjectProvider<AsyncOnCompleteListenerWrapper> asyncOnCompleteListenerWrapper) {
			final var logbookFilter = new LogbookServletFilter(logbook, bodyCapturePolicy, asyncOnCompleteListenerWrapper.getIfAvailable(AsyncOnCompleteListenerWrapper::identity));
			return registration(logbookFilter, FILTER_NAME, Ordered.LOWEST_PRECEDENCE);
		}

		@Bean(SECURE_FILTER_NAME)
		@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
		@ConditionalOnProperty(name = "logbook.secure-filter.enabled", havingValue = "true", matchIfMissing = true)
		@ConditionalOnMissingBean(name = SECURE_FILTER_NAME)
		FilterRegistrationBean<LogbookServletFilter> secureLogbookFilter(final Logbook logbook, final BodyCapturePolicy bodyCapturePolicy,
			final ObjectProvider<AsyncOnCompleteListenerWrapper> asyncOnCompleteListenerWrapper) {
			final var secureLogbookFilter = LogbookServletFilter.secure(logbook, bodyCapturePolicy, asyncOnCompleteListenerWrapper.getIfAvailable(AsyncOnCompleteListenerWrapper::identity));
			return registration(secureLogbookFilter, SECURE_FILTER_NAME, Ordered.HIGHEST_PRECEDENCE + 1);
		}

		private static <T extends Filter> FilterRegistrationBean<T> registration(final T filter, final String name, final int order) {
			final var registration = new FilterRegistrationBean<>(filter);
			registration.setName(name);
			registration.setDispatcherTypes(REQUEST, ASYNC);
			registration.setOrder(order);
			return registration;
		}
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
