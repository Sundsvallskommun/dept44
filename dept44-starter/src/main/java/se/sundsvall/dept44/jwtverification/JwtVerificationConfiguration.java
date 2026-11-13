package se.sundsvall.dept44.jwtverification;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.core.HeaderFilters;
import se.sundsvall.dept44.jwtverification.JwtVerificationProperties.Verifier;
import tools.jackson.databind.json.JsonMapper;

import static jakarta.servlet.DispatcherType.REQUEST;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * Registers {@link JwtVerificationFilter} when {@code dept44.jwt-verification.enabled} is {@code true}.
 * <p>
 * The decoders are created here and not exposed as beans: a {@link JwtDecoder} bean would make Spring Boot's
 * resource-server auto-configuration back off in a service that relies on it.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(prefix = JwtVerificationProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JwtVerificationProperties.class)
public class JwtVerificationConfiguration {

	static final String FILTER_NAME = "jwtVerificationFilter";

	/**
	 * After dept44's request id, identifier and municipality id filters (orders 1 and 2), so that a rejection is logged
	 * with the request id. The secure Logbook filter runs before, so rejected requests are still payload-logged.
	 */
	static final int FILTER_ORDER = 3;

	/**
	 * What Logbook itself writes in place of a masked header.
	 */
	static final String MASK = "XXX";

	private static final Logger LOGGER = LoggerFactory.getLogger(JwtVerificationConfiguration.class);

	@Bean(FILTER_NAME)
	FilterRegistrationBean<JwtVerificationFilter> jwtVerificationFilter(final JwtVerificationProperties properties, final Environment environment, final JsonMapper jsonMapper,
		final ObjectProvider<HandlerMappingIntrospector> handlerMappingIntrospector) {
		requireDistinctHeaders(properties);

		final var builtIn = JwtVerificationExclusions.builtIn(environment);
		final var excluded = JwtVerificationExclusions.configured(environment, properties.excludedPaths());
		LOGGER.info("JWT verification skips {} when served by their own endpoints, and {}", builtIn, excluded);

		final var verifiers = properties.verifiers().entrySet().stream()
			.map(entry -> {
				final var name = entry.getKey();
				final var verifier = entry.getValue();
				final var verifierExcluded = JwtVerificationExclusions.configured(environment, verifier.excludedPaths());
				LOGGER.info("JWT verifier '{}' verifies header '{}' with the keys at {}, except for {}", name, verifier.headerName(), verifier.jwkSetUris(), verifierExcluded);
				final var decoder = JwtVerificationDecoders.create(name, verifier);
				return new JwtVerifier(name, verifier.headerName(), parse(verifierExcluded), decoder.jwtDecoder(), decoder.keySource());
			})
			.toList();

		// The introspector is looked up on first use: the filter is created before the DispatcherServlet's handler mappings
		final var builtInEndpoints = new BuiltInEndpoints(handlerMappingIntrospector);
		final var filter = new JwtVerificationFilter(parse(builtIn), builtInEndpoints::serve, parse(excluded), verifiers, jsonMapper);
		final var registration = new FilterRegistrationBean<>(filter);
		registration.setName(FILTER_NAME);
		registration.setDispatcherTypes(REQUEST);
		registration.setOrder(FILTER_ORDER);
		return registration;
	}

	/**
	 * Masks the verified headers in the payload log: a logged JWT could be copied and sent to the service directly until
	 * it expires, and its claims may hold personal data.
	 */
	@Bean
	HeaderFilter jwtVerificationHeaderFilter(final JwtVerificationProperties properties) {
		final var headerNames = properties.verifiers().values().stream()
			.map(Verifier::headerName)
			.toList();
		return HeaderFilters.replaceHeaders(headerName -> headerNames.stream().anyMatch(headerName::equalsIgnoreCase), MASK);
	}

	/**
	 * Two verifiers on one header would both have to accept the same JWT, which is a mistake rather than a setup.
	 */
	private static void requireDistinctHeaders(final JwtVerificationProperties properties) {
		final var verifierByHeader = new HashMap<String, String>();
		properties.verifiers().forEach((name, verifier) -> {
			final var other = verifierByHeader.putIfAbsent(verifier.headerName().toLowerCase(Locale.ROOT), name);
			if (other != null) {
				throw new IllegalStateException("JWT verifiers '%s' and '%s' both verify header '%s'; each verifier needs a header of its own"
					.formatted(other, name, verifier.headerName()));
			}
		});
	}

	private static List<PathPattern> parse(final List<String> patterns) {
		return patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
	}
}
