package se.sundsvall.dept44.jwtverification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * The paths that are not verified.
 * <p>
 * The built-in ones are the paths whose callers do not send a JWT:
 * <ul>
 * <li>the index page {@code /} (exact match only) and what it links to: the OpenAPI document as JSON and YAML, the
 * Swagger UI and its assets;</li>
 * <li>actuator, which Spring Boot Admin and the Kubernetes probes call directly.</li>
 * </ul>
 * They are read from the properties that place those endpoints, so a service that moves them stays covered. Matching
 * one of them is not enough on its own: the filter also checks that the request is really served by one of those
 * endpoints, see {@link BuiltInEndpoints}.
 * <p>
 * All paths, built-in and configured, are prefixed with {@code spring.mvc.servlet.path}, which the filter sees as part
 * of the path but controller mappings do not.
 */
final class JwtVerificationExclusions {

	static final String SERVLET_PATH = "spring.mvc.servlet.path";
	static final String MANAGEMENT_BASE_PATH = "management.endpoints.web.base-path";
	static final String API_DOCS_PATH = "springdoc.api-docs.path";
	static final String SWAGGER_UI_PATH = "springdoc.swagger-ui.path";

	// Spring Boot's and springdoc's own defaults, for when a property is not set
	private static final String DEFAULT_MANAGEMENT_BASE_PATH = "/actuator";
	private static final String DEFAULT_API_DOCS_PATH = "/v3/api-docs";
	private static final String DEFAULT_SWAGGER_UI_PATH = "/swagger-ui.html";

	private JwtVerificationExclusions() {}

	/**
	 * @param  environment the environment to read the endpoint paths from
	 * @return             the built-in exclusions
	 */
	static List<String> builtIn(final Environment environment) {
		final var managementBasePath = path(environment, MANAGEMENT_BASE_PATH, DEFAULT_MANAGEMENT_BASE_PATH);
		if ("/".equals(managementBasePath)) {
			// Excluding everything below "/" would turn verification off for the whole service
			throw new IllegalStateException("Actuator at the root path can not be excluded from JWT verification. Set '%s' to a path of its own, e.g. /actuator"
				.formatted(MANAGEMENT_BASE_PATH));
		}

		final var apiDocsPath = path(environment, API_DOCS_PATH, DEFAULT_API_DOCS_PATH);
		final var swaggerUiPath = path(environment, SWAGGER_UI_PATH, DEFAULT_SWAGGER_UI_PATH);
		final var swaggerUiParent = swaggerUiPath.substring(0, swaggerUiPath.lastIndexOf('/'));
		final var servletPath = servletPath(environment);

		final var exclusions = new LinkedHashSet<String>();
		if (servletPath.isEmpty()) {
			exclusions.add("/");
		} else {
			// The index page of a DispatcherServlet mapped to /api is /api/, and /api leads there too
			exclusions.add(servletPath);
			exclusions.add(servletPath + "/");
		}
		// "/x/**" matches "/x" too
		exclusions.add(servletPath + managementBasePath + "/**");
		exclusions.add(servletPath + apiDocsPath + ".yaml");
		exclusions.add(servletPath + apiDocsPath + "/**");
		exclusions.add(servletPath + swaggerUiPath);
		exclusions.add(servletPath + swaggerUiParent + "/swagger-ui/**");
		exclusions.add(servletPath + "/swagger-ui/**");
		exclusions.add(servletPath + "/webjars/**");
		exclusions.add(servletPath + "/favicon.ico");
		return new ArrayList<>(exclusions);
	}

	/**
	 * @param  environment the environment to read {@code spring.mvc.servlet.path} from
	 * @param  patterns    configured exclusions, relative to the servlet path like controller mappings
	 * @return             the patterns as the filter sees the paths, without duplicates
	 */
	static List<String> configured(final Environment environment, final List<String> patterns) {
		final var servletPath = servletPath(environment);
		return patterns.stream()
			.map(pattern -> servletPath + (pattern.startsWith("/") ? pattern : "/" + pattern))
			.distinct()
			.toList();
	}

	/**
	 * @return the DispatcherServlet's path without a trailing slash, empty when it is mapped to the root
	 */
	private static String servletPath(final Environment environment) {
		final var servletPath = path(environment, SERVLET_PATH, "/");
		return "/".equals(servletPath) ? "" : servletPath;
	}

	private static String path(final Environment environment, final String property, final String defaultPath) {
		final var value = environment.getProperty(property, defaultPath).trim();
		if (!StringUtils.hasText(value)) {
			return "/";
		}
		final var withLeadingSlash = value.startsWith("/") ? value : "/" + value;
		return withLeadingSlash.length() > 1 && withLeadingSlash.endsWith("/") ? withLeadingSlash.substring(0, withLeadingSlash.length() - 1) : withLeadingSlash;
	}
}
