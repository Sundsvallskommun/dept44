package se.sundsvall.dept44.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

import se.sundsvall.dept44.authorization.configuration.JwtAuthorizationProperties;
import se.sundsvall.dept44.authorization.configuration.PrePostMethodSecurityConfiguration;
import se.sundsvall.dept44.authorization.configuration.UnauthorizedExceptionHandlerConfiguration;

class EnableJwtAuthorizationTest {

	@Test
	void verifyTargetAnnotation() {
		Target annotation = getAnnotation(EnableJwtAuthorization.class, Target.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactly(ElementType.TYPE);
	}

	@Test
	void verifyRetentionAnnotation() {
		Retention annotation = getAnnotation(EnableJwtAuthorization.class, Retention.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).isEqualTo(RetentionPolicy.RUNTIME);
	}

	@Test
	void verifyImportAnnotation() {
		Import annotation = getAnnotation(EnableJwtAuthorization.class, Import.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactlyInAnyOrder(
			PrePostMethodSecurityConfiguration.class,
			UnauthorizedExceptionHandlerConfiguration.class,
			JwtAuthorizationProperties.class);
	}
}
