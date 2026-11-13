package se.sundsvall.dept44.jwtverification;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Tells whether a request is served by one of the endpoints that the built-in exclusions are meant for: actuator, the
 * OpenAPI documents, Swagger UI, static resources and dept44's index page.
 * <p>
 * A path matching a built-in exclusion is not enough, because the handler is chosen separately: with a controller
 * mapped to {@code /{municipalityId}/{id}/things}, {@code /actuator/1/things} matches {@code /actuator/**}
 * but is served by the controller, with {@code municipalityId} "actuator". So the handler is resolved with the
 * DispatcherServlet's own handler mappings, in its order, and only a handler from Spring Boot, Spring MVC itself,
 * springdoc or dept44's index page counts. Without the mappings the request is not counted as built-in, so it is
 * verified.
 */
class BuiltInEndpoints {

	private static final List<String> BUILT_IN_PACKAGES = List.of(
		"org.springframework.boot.", // Actuator
		"org.springframework.web.servlet.", // Static resources (webjars, Swagger UI), CORS pre-flight
		"org.springdoc."); // OpenAPI documents, Swagger UI

	/**
	 * Dept44's index page and its OpenAPI YAML. By name and not by package: services' own classes may live in dept44's
	 * packages too.
	 */
	static final String INDEX_PAGE_CONTROLLER = "se.sundsvall.dept44.configuration.WebConfiguration$IndexPageController";

	private final DispatcherServlet dispatcherServlet;

	BuiltInEndpoints(final DispatcherServlet dispatcherServlet) {
		this.dispatcherServlet = dispatcherServlet;
	}

	/**
	 * @param  request the request, whose path matches a built-in exclusion
	 * @return         {@code true} if a built-in endpoint serves it, or no handler does (the response is an error such as
	 *                 404 or 406); {@code false} if another handler serves it, or the handler mappings are not known
	 */
	boolean serve(final HttpServletRequest request) {
		// Set when the DispatcherServlet is initialized, which the container does before it runs the filters
		final var mappings = dispatcherServlet.getHandlerMappings();
		if (mappings == null || mappings.isEmpty()) {
			return false;
		}

		// The handler mappings record what they matched in request attributes; keep that away from the real request
		final var shielded = new AttributeShieldingRequest(request);
		ServletRequestPathUtils.parseAndCache(shielded);
		for (final var mapping : mappings) {
			final HandlerExecutionChain chain;
			try {
				chain = mapping.getHandler(shielded);
			} catch (final Exception _) {
				// A path that matched but a method or media type that does not: the DispatcherServlet answers with an error
				// (405, 406 …) and runs no handler
				return true;
			}
			if (chain != null) {
				return isBuiltIn(chain.getHandler());
			}
		}
		return true;
	}

	private static boolean isBuiltIn(final Object handler) {
		final var type = handler instanceof final HandlerMethod method ? method.getBeanType() : handler.getClass();
		return INDEX_PAGE_CONTROLLER.equals(type.getName()) || BUILT_IN_PACKAGES.stream().anyMatch(type.getName()::startsWith);
	}

	/**
	 * Request attributes in a map of its own, seeded from the request, so resolving the handler leaves the real request as
	 * it was.
	 */
	static final class AttributeShieldingRequest extends HttpServletRequestWrapper {

		private final Map<String, Object> attributes = new HashMap<>();

		AttributeShieldingRequest(final HttpServletRequest request) {
			super(request);
			Collections.list(request.getAttributeNames()).forEach(name -> attributes.put(name, request.getAttribute(name)));
		}

		@Override
		public Object getAttribute(final String name) {
			return attributes.get(name);
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			return Collections.enumeration(List.copyOf(attributes.keySet()));
		}

		@Override
		public void setAttribute(final String name, final Object value) {
			if (value == null) {
				attributes.remove(name);
			} else {
				attributes.put(name, value);
			}
		}

		@Override
		public void removeAttribute(final String name) {
			attributes.remove(name);
		}
	}
}
