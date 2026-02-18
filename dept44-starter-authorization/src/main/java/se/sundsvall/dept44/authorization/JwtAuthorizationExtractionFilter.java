package se.sundsvall.dept44.authorization;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import se.sundsvall.dept44.ServiceApplication;
import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import se.sundsvall.dept44.authorization.model.User;
import se.sundsvall.dept44.authorization.model.UsernameAuthenticationToken;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

public class JwtAuthorizationExtractionFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthorizationExtractionFilter.class);
	private static final String EXCEPTION_UNREADABLE_CREDENTIALS = "Credentials could not be read";
	private static final String EXCEPTION_INVALID_SIGNATURE = "Invalid signature detected for credentials";
	private static final String EXCEPTION_CREDENTIALS_EXPIRED = "Credentials has expired";
	private static final String EXCEPTION_WEAK_KEY = "The verification key's size is not secure enough for the selected algorithm";
	private static final String EXCEPTION_UNHANDLED = "Exception occurred when reading credentials";

	private final JwtAuthorizationProperties properties;
	private final JwtTokenUtil jwtTokenUtil;
	private final WebAuthenticationDetailsSource webAuthenticationDetailsSource;
	private final ApplicationContext applicationContext;
	private final JsonMapper jsonMapper;

	public JwtAuthorizationExtractionFilter(
		final JwtAuthorizationProperties properties,
		final JwtTokenUtil jwtTokenUtil,
		final WebAuthenticationDetailsSource webAuthenticationDetailsSource,
		final ApplicationContext applicationContext,
		final JsonMapper jsonMapper) {

		this.properties = properties;
		this.jwtTokenUtil = jwtTokenUtil;
		this.webAuthenticationDetailsSource = webAuthenticationDetailsSource;
		this.applicationContext = applicationContext;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Method checks if filter should be applied or not by finding the class annotated with <code>@ServiceApplication</code>
	 * and verifying if it also has been annotated with <code>@EnableJwtAuthorization</code>. If
	 * <code>@EnableJwtAuthorization</code> is present on application class this
	 * filter will be triggered, otherwise not.
	 */
	@Override
	protected boolean shouldNotFilter(final HttpServletRequest request) {
		final var matches = applicationContext.getBeansWithAnnotation(ServiceApplication.class);
		return matches.entrySet().stream()
			.findAny()
			.map(Entry::getValue)
			.map(Object::getClass)
			.map(clazz -> AnnotationUtils.getAnnotation(clazz, EnableJwtAuthorization.class))
			.isPresent();
	}

	/**
	 * Method extracts jwt token from the request header. Header name can be configured by setting property
	 * <code>jwt.authorization.headername</code> in service. Default name, if header name is not explicitly set, is
	 * <code>x-authorization-info</code>. The token is validated and transformed
	 * into a <code>UsernameAuthenticationToken</code> which is then placed into the SecurityContext to enable Springs
	 * authorization annotations to access it.
	 */
	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws ServletException, IOException {
		final String jwtToken = request.getHeader(properties.getHeaderName());

		if (nonNull(jwtToken)) {
			LOGGER.debug("Token present, continuing with extraction of jwt token");
			extractToken(request, response, chain, jwtToken);
		} else {
			LOGGER.debug("Token not present, continuing with next filter in chain");
			chain.doFilter(request, response);
		}
	}

	private void extractToken(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain, final String jwtToken) throws IOException {
		try {
			// Read JWT-token and fetch username and accesses from it
			final String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
			final Collection<GenericGrantedAuthority> authorities = jwtTokenUtil.getRolesFromToken(jwtToken);

			// Validate and store the token in SecurityContext if it isn't stored already
			if (nonNull(username) && isNull(SecurityContextHolder.getContext().getAuthentication())) {
				final UserDetails userDetails = createUserDetails(username, authorities);
				final UsernameAuthenticationToken authenticationToken = UsernameAuthenticationToken.authenticated(userDetails, authorities);
				authenticationToken.setDetails(webAuthenticationDetailsSource.buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authenticationToken);
			}

			// Continue with the next filter in a filter chain
			chain.doFilter(request, response);

		} catch (final IllegalArgumentException | MalformedJwtException | UnsupportedJwtException e) {
			handleException(response, e, EXCEPTION_UNREADABLE_CREDENTIALS);
		} catch (final SignatureException e) {
			handleException(response, e, EXCEPTION_INVALID_SIGNATURE);
		} catch (final ExpiredJwtException e) {
			handleException(response, e, EXCEPTION_CREDENTIALS_EXPIRED);
		} catch (final WeakKeyException e) {
			handleException(response, e, EXCEPTION_WEAK_KEY);
		} catch (final Exception e) {
			handleException(response, e, EXCEPTION_UNHANDLED);
		}
	}

	private UserDetails createUserDetails(final String username, final Collection<GenericGrantedAuthority> authorities) {
		return User.create().withUsername(username).withAuthorities(authorities);
	}

	private void handleException(final HttpServletResponse response, final Exception exception, final String title) throws IOException {
		LOGGER.error("Exception occurred when processing the jwt token", exception);

		response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.getWriter().write(jsonMapper.writeValueAsString(createProblem(exception, title)));
	}

	private ThrowableProblem createProblem(final Exception exception, final String title) {
		return Problem.builder()
			.withDetail(extractMessage(exception))
			.withStatus(UNAUTHORIZED).withTitle(title)
			.build();
	}

	private String extractMessage(final Exception e) {
		return ofNullable(e.getMessage()).orElse(String.valueOf(e));
	}
}
