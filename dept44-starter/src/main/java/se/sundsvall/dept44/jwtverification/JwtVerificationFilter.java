package se.sundsvall.dept44.jwtverification;

import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import se.sundsvall.dept44.problem.Problem;
import tools.jackson.databind.json.JsonMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.util.StringUtils.hasText;

/**
 * Rejects a request unless every configured verifier accepts the JWT in its header: the signature must verify against
 * the verifier's keys, and the JWT must have an expiry that has not passed. A missing, invalid or expired JWT gives
 * 401; keys that can not be fetched, or can not be fetched again yet, give 503.
 * <p>
 * Not verified are:
 * <ul>
 * <li>requests to the built-in exclusions (the index page, OpenAPI documents, Swagger UI and actuator), provided the
 * endpoint they are meant for really serves them;</li>
 * <li>paths the service excludes, for all verifiers or for one. They are matched on the path alone, so a pattern ending
 * in {@code /**} also exempts any controller whose path starts with a variable: list exact paths.</li>
 * </ul>
 * The claims are not used: the filter is a gate and puts nothing into the security context.
 */
public class JwtVerificationFilter extends OncePerRequestFilter {

	static final String TITLE_MISSING = "Missing JWT";
	static final String TITLE_INVALID = "JWT is not valid";
	static final String TITLE_UNVERIFIED = "JWT could not be verified";
	static final String DETAIL_MISSING = "Header '%s' is missing";
	static final String DETAIL_KEYS_UNAVAILABLE = "The keys to verify the JWT in header '%s' could not be fetched";
	static final String DETAIL_KEYS_RATE_LIMITED = "The keys to verify the JWT in header '%s' were fetched moments ago and can not be fetched again yet";

	/**
	 * Paths as the container resolved them, so not decoded again and without path parameters: decoding twice would turn
	 * "/%2561ctuator" into "/actuator".
	 */
	private static final PathContainer.Options PATH_OPTIONS = PathContainer.Options.create('/', false);

	private static final Logger LOGGER = LoggerFactory.getLogger(JwtVerificationFilter.class);

	private final List<PathPattern> builtInPaths;
	private final Predicate<HttpServletRequest> builtInEndpoint;
	private final List<PathPattern> excludedPaths;
	private final List<JwtVerifier> verifiers;
	private final JsonMapper jsonMapper;

	/**
	 * @param builtInPaths    the built-in exclusions
	 * @param builtInEndpoint tells whether a request matching a built-in exclusion is served by the endpoint it is meant
	 *                        for
	 * @param excludedPaths   paths the service excludes for all verifiers
	 * @param verifiers       the verifiers, all of which must pass, checked in this order
	 * @param jsonMapper      writes the problem response
	 */
	public JwtVerificationFilter(final List<PathPattern> builtInPaths, final Predicate<HttpServletRequest> builtInEndpoint, final List<PathPattern> excludedPaths,
		final List<JwtVerifier> verifiers, final JsonMapper jsonMapper) {
		this.builtInPaths = List.copyOf(builtInPaths);
		this.builtInEndpoint = builtInEndpoint;
		this.excludedPaths = List.copyOf(excludedPaths);
		this.verifiers = List.copyOf(verifiers);
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws ServletException, IOException {
		final var path = path(request);
		if (!isExcluded(path, request)) {
			for (final var verifier : verifiers) {
				if (!verifier.excludes(path) && !verify(verifier, request, response)) {
					return;
				}
			}
		}

		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		verifiers.forEach(JwtVerifier::close);
	}

	private boolean isExcluded(final PathContainer path, final HttpServletRequest request) {
		return JwtVerifier.matches(excludedPaths, path) || (JwtVerifier.matches(builtInPaths, path) && builtInEndpoint.test(request));
	}

	/**
	 * @return {@code true} if the JWT verified, {@code false} if the request was rejected
	 */
	private boolean verify(final JwtVerifier verifier, final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		final var headerName = verifier.headerName();
		final var jwt = request.getHeader(headerName);
		if (!hasText(jwt)) {
			reject(verifier, request, response, TITLE_MISSING, DETAIL_MISSING.formatted(headerName));
			return false;
		}

		try {
			// Some issuers encode the signature with standard Base64 instead of base64url; Nimbus decodes both
			verifier.jwtDecoder().decode(jwt);
			return true;
		} catch (final JwtValidationException e) {
			// Expired or not yet valid. Checked before BadJwtException, which it extends
			reject(verifier, request, response, TITLE_INVALID, e.getMessage());
		} catch (final BadJwtException e) {
			// Malformed, wrong signature or a key id the issuer does not publish
			reject(verifier, request, response, TITLE_UNVERIFIED, e.getMessage());
		} catch (final JwtException e) {
			if (e.getCause() instanceof RateLimitReachedException) {
				// The keys were needed again within moments of the last fetches: none could be fetched yet, or the JWT has a
				// key id they don't hold, which a new key published since then may. Either way a retry can pass, so 503. A
				// warning only: an endpoint that is down was logged as an error by the fetch that failed
				LOGGER.warn("JWT verifier '{}' could not verify a {} request: the keys can not be fetched again yet", verifier.name(), request.getMethod());
				writeProblem(response, SERVICE_UNAVAILABLE, TITLE_UNVERIFIED, DETAIL_KEYS_RATE_LIMITED.formatted(headerName));
			} else {
				// The keys could not be fetched; nothing the caller did wrong, so 503 and an error in the log
				LOGGER.error("JWT verifier '{}' could not verify a {} request", verifier.name(), request.getMethod(), e);
				writeProblem(response, SERVICE_UNAVAILABLE, TITLE_UNVERIFIED, DETAIL_KEYS_UNAVAILABLE.formatted(headerName));
			}
		}
		return false;
	}

	/**
	 * The path the container resolved (servlet path + path info), not the raw request URI: "/actuator/../things" is
	 * routed to "/things" and must not count as actuator.
	 */
	private static PathContainer path(final HttpServletRequest request) {
		return PathContainer.parsePath(request.getServletPath() + Objects.toString(request.getPathInfo(), ""), PATH_OPTIONS);
	}

	/**
	 * Logs without the path, which may hold personal data; the request id ties the line to the request log.
	 */
	private void reject(final JwtVerifier verifier, final HttpServletRequest request, final HttpServletResponse response, final String title, final String detail) throws IOException {
		LOGGER.warn("JWT verifier '{}' rejected a {} request: {}: {}", verifier.name(), request.getMethod(), title, detail);
		writeProblem(response, UNAUTHORIZED, title, detail);
	}

	private void writeProblem(final HttpServletResponse response, final HttpStatus status, final String title, final String detail) throws IOException {
		final var problem = Problem.builder()
			.withStatus(status)
			.withTitle(title)
			.withDetail(detail)
			.build();

		response.setStatus(status.value());
		response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(UTF_8);
		response.getWriter().write(jsonMapper.writeValueAsString(problem));
	}
}
