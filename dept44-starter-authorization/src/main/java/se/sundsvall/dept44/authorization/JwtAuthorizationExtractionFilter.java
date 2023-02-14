package se.sundsvall.dept44.authorization;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.zalando.problem.Status.UNAUTHORIZED;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import io.jsonwebtoken.UnsupportedJwtException;
import se.sundsvall.dept44.ServiceApplication;
import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import se.sundsvall.dept44.authorization.model.User;
import se.sundsvall.dept44.authorization.model.UsernameAuthenticationToken;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;

public class JwtAuthorizationExtractionFilter extends OncePerRequestFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthorizationExtractionFilter.class);
	private static final String EXCEPTION_UNREADABLE_CREDENTIALS = "Credentials could not be read";
	private static final String EXCEPTION_INVALID_SIGNATURE = "Invalid signature detected for credentials";
	private static final String EXCEPTION_CREDENTIALS_EXPIRED = "Credentials has expired";
	private static final String EXCEPTION_WEAK_KEY = "The verification key's size is not secure enough for the selected algorithm";
	private static final String EXCEPTION_UNHANDLED = "Exception occurred when reading credentials";

	@Autowired
	private JwtAuthorizationProperties properties;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private WebAuthenticationDetailsSource webAuthenticationDetailsSource;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Method checks if filter should be applied or not by finding the class
	 * annotated with <code>@ServiceApplication</code> and verifying if it also has
	 * been annotated with <code>@EnableJwtAuthorization</code>. If
	 * <code>@EnableJwtAuthorization</code> is present on application class this
	 * filter will be triggered, otherwise not.
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		final var matches = applicationContext.getBeansWithAnnotation(ServiceApplication.class);
		return matches.entrySet().stream()
			.findAny()
			.map(Entry::getValue)
			.map(Object::getClass)
			.map(clazz -> AnnotationUtils.getAnnotation(clazz, EnableJwtAuthorization.class))
			.isPresent();
	}

	/**
	 * Method extracts jwt token from request header. Header name can be configured
	 * by setting property <code>jwt.authorization.headername</code> in service.
	 * Default name, if header name is not explicitly set, is
	 * <code>x-authorization-info</code>. The token is validated and transformed
	 * into a <code>UsernameAuthenticationToken</code> which is then placed into the
	 * SecurityContext to enable Springs authorization annotations to access it.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		String jwtToken = request.getHeader(properties.getHeaderName());

		if (nonNull(jwtToken)) {
			LOGGER.debug("Token present, continuing with extraction of jwt token");
			extractToken(request, response, chain, jwtToken);
		} else {
			LOGGER.debug("Token not present, continuing with next filter in chain");
			chain.doFilter(request, response);
		}
	}

	private void extractToken(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String jwtToken) throws IOException {
		try {
			// Read JWT-token and fetch user name and accesses from it
			String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
			Collection<GenericGrantedAuthority> authorities = jwtTokenUtil.getRolesFromToken(jwtToken);

			// Validate and store token in SecurityContext if it isn't stored already
			if (nonNull(username) && isNull(SecurityContextHolder.getContext().getAuthentication())) {
				UserDetails userDetails = createUserDetails(username, authorities);
				UsernameAuthenticationToken authenticationToken = UsernameAuthenticationToken.authenticated(userDetails, authorities);
				authenticationToken.setDetails(webAuthenticationDetailsSource.buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authenticationToken);
			}

			// Continue with next filter in filter chain
			chain.doFilter(request, response);

		} catch (IllegalArgumentException | MalformedJwtException | UnsupportedJwtException e) {
			handleException(response, e, EXCEPTION_UNREADABLE_CREDENTIALS);
		} catch (SignatureException e) {
			handleException(response, e, EXCEPTION_INVALID_SIGNATURE);
		} catch (ExpiredJwtException e) {
			handleException(response, e, EXCEPTION_CREDENTIALS_EXPIRED);
		} catch (WeakKeyException e) {
			handleException(response, e, EXCEPTION_WEAK_KEY);
		} catch (Exception e) {
			handleException(response, e, EXCEPTION_UNHANDLED);
		}
	}

	private UserDetails createUserDetails(String username, Collection<GenericGrantedAuthority> authorities) {
		return User.create().withUsername(username).withAuthorities(authorities);
	}

	private void handleException(HttpServletResponse response, Exception exception, String title) throws IOException {
		LOGGER.error("Exception occrurred when processing the jwt token", exception);

		response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.getWriter().write(objectMapper.writeValueAsString(createProblem(exception, title)));
	}

	private ThrowableProblem createProblem(Exception exception, String title) {
		return Problem.builder()
			.withDetail(extractMessage(exception))
			.withStatus(UNAUTHORIZED).withTitle(title)
			.build();
	}

	private String extractMessage(Exception e) {
		return ofNullable(e.getMessage()).orElse(String.valueOf(e));
	}
}
