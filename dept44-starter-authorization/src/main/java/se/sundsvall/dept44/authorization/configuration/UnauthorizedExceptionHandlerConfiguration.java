package se.sundsvall.dept44.authorization.configuration;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zalando.problem.Problem;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.zalando.problem.Status.UNAUTHORIZED;

@Configuration
@ConditionalOnProperty(name = "jwt.authorization.secret")
public class UnauthorizedExceptionHandlerConfiguration { // NOSONAR

	private static final Logger LOGGER = LoggerFactory.getLogger(UnauthorizedExceptionHandlerConfiguration.class);

	private static final String LOG_MESSAGE = "Translating exception to problem";

	private static String extractMessage(Exception e) {
		return Optional.ofNullable(e.getMessage()).orElse(String.valueOf(e));
	}

	private static ResponseEntity<Problem> createResponseEntity(Exception exception) {
		LOGGER.info(LOG_MESSAGE, exception);

		final var errorResponse = Problem.builder()
			.withStatus(UNAUTHORIZED)
			.withTitle(UNAUTHORIZED.getReasonPhrase())
			.withDetail(extractMessage(exception))
			.build();

		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(APPLICATION_PROBLEM_JSON).body(errorResponse);
	}

	@ControllerAdvice
	public static class AuthenticationCredentialsNotFoundExceptionHandler {
		@ExceptionHandler
		@ResponseBody
		public ResponseEntity<Problem> handleException(AuthenticationCredentialsNotFoundException exception) {
			return createResponseEntity(exception);
		}
	}

	@ControllerAdvice
	public static class AccessDeniedExceptionHandler {
		@ExceptionHandler
		@ResponseBody
		public ResponseEntity<Problem> handleException(AccessDeniedException exception) {
			return createResponseEntity(exception);
		}
	}
}
