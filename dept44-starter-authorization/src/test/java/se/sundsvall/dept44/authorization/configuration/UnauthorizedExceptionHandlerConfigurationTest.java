package se.sundsvall.dept44.authorization.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.zalando.problem.Status;

import se.sundsvall.dept44.authorization.configuration.UnauthorizedExceptionHandlerConfiguration.AccessDeniedExceptionHandler;
import se.sundsvall.dept44.authorization.configuration.UnauthorizedExceptionHandlerConfiguration.AuthenticationCredentialsNotFoundExceptionHandler;

class UnauthorizedExceptionHandlerConfigurationTest {

	@Test
	void verifyConfigurationAnnotation() {
		assertThat(getAnnotation(UnauthorizedExceptionHandlerConfiguration.class, Configuration.class)).isNotNull();
	}

	@Test
	void verifyConditionalOnPropertyAnnotation() {
		ConditionalOnProperty annotation = getAnnotation(UnauthorizedExceptionHandlerConfiguration.class, ConditionalOnProperty.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.name()).containsExactly("jwt.authorization.secret");
		assertThat(annotation.matchIfMissing()).isFalse();
		assertThat(annotation.havingValue()).isNullOrEmpty();
		assertThat(annotation.prefix()).isNullOrEmpty();
	}

	@ParameterizedTest
	@ValueSource(classes = {
		AuthenticationCredentialsNotFoundExceptionHandler.class, AccessDeniedExceptionHandler.class
	})
	void verifyHandlerMethodAnnotation(Class<?> handler) {
		assertThat(getAnnotation(handler, ControllerAdvice.class)).isNotNull();

		Stream.of(ReflectionUtils.getDeclaredMethods(handler))
			.filter(method -> !method.isSynthetic()) // Need to remove synthetic methods added by j-unit ($jacocoInit)
			.forEach(method -> {
				assertThat(getAnnotation(method, ExceptionHandler.class)).isNotNull();
				assertThat(getAnnotation(method, ResponseBody.class)).isNotNull();
			});
	}

	@Test
	void verifyProblemResponseForAuthenticationCredentialsNotFoundException() {
		final var exceptionMessage = "exceptionMessage";
		final var exception = new AuthenticationCredentialsNotFoundException(exceptionMessage);

		final var entity = new AuthenticationCredentialsNotFoundExceptionHandler().handleException(exception);

		assertThat(entity.getStatusCode()).isEqualTo(UNAUTHORIZED);
		assertThat(entity.getBody().getStatus()).isEqualTo(Status.UNAUTHORIZED);
		assertThat(entity.getBody().getTitle()).isEqualTo(Status.UNAUTHORIZED.getReasonPhrase());
		assertThat(entity.getBody().getDetail()).isEqualTo(exceptionMessage);
	}

	@Test
	void verifyProblemResponseForAccessDeniedException() {
		final var exceptionMessage = "exceptionMessage";
		final var exception = new AccessDeniedException(exceptionMessage);

		final var entity = new AccessDeniedExceptionHandler().handleException(exception);

		assertThat(entity.getStatusCode()).isEqualTo(UNAUTHORIZED);
		assertThat(entity.getBody().getStatus()).isEqualTo(Status.UNAUTHORIZED);
		assertThat(entity.getBody().getTitle()).isEqualTo(Status.UNAUTHORIZED.getReasonPhrase());
		assertThat(entity.getBody().getDetail()).isEqualTo(exceptionMessage);
	}
}
