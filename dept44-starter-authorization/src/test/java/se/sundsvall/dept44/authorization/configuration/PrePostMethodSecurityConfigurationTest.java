package se.sundsvall.dept44.authorization.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.ReflectionUtils;

import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;

class PrePostMethodSecurityConfigurationTest {

	@Test
	void verifyConfigurationAnnotation() {
		assertThat(getAnnotation(PrePostMethodSecurityConfiguration.class, Configuration.class)).isNotNull();
	}

	@Test
	void verifyEnableGlobalMethodSecurityAnnotation() {
		EnableGlobalMethodSecurity annotation = getAnnotation(PrePostMethodSecurityConfiguration.class, EnableGlobalMethodSecurity.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.prePostEnabled()).isTrue();
		assertThat(annotation.securedEnabled()).isFalse();
		assertThat(annotation.jsr250Enabled()).isFalse();
	}

	@Test
	void verifyBeanAnnotation() {
		Stream.of(ReflectionUtils.getDeclaredMethods(PrePostMethodSecurityConfiguration.class))
			.filter(method -> !method.isSynthetic()) // Need to remove synthetic methods added by j-unit ($jacocoInit)
			.forEach(method -> assertThat(getAnnotation(method, Bean.class)).isNotNull());
	}

	@Test
	void verifyBeanCreation() {
		final var secret = "secret";
		JwtAuthorizationProperties properties = new JwtAuthorizationProperties();
		properties.setSecret(secret);

		PrePostMethodSecurityConfiguration configuration = new PrePostMethodSecurityConfiguration();

		assertThat(configuration.jwtAuthorizationExtractionFilter()).isNotNull().isInstanceOf(JwtAuthorizationExtractionFilter.class);
		assertThat(configuration.webAuthenticationDetailsSource()).isNotNull().isInstanceOf(WebAuthenticationDetailsSource.class);
		assertThat(configuration.jwtTokenUtil(properties)).isNotNull().hasFieldOrPropertyWithValue("secret", secret.getBytes());
	}
}
