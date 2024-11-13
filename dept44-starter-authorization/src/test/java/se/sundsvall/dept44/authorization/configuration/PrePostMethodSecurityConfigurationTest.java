package se.sundsvall.dept44.authorization.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.ReflectionUtils;
import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;

@ExtendWith(MockitoExtension.class)
class PrePostMethodSecurityConfigurationTest {

	@Test
	void verifyConfigurationAnnotation() {
		assertThat(getAnnotation(PrePostMethodSecurityConfiguration.class, Configuration.class)).isNotNull();
	}

	@Test
	void verifyEnableGlobalMethodSecurityAnnotation() {
		final EnableMethodSecurity annotation = getAnnotation(PrePostMethodSecurityConfiguration.class,
			EnableMethodSecurity.class);

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
	void verifyBeanCreation(
		@Mock JwtAuthorizationProperties properties,
		@Mock JwtTokenUtil jwtTokenUtil,
		@Mock WebAuthenticationDetailsSource webAuthenticationDetailsSource,
		@Mock ApplicationContext applicationContext,
		@Mock ObjectMapper objectMapper) {

		final var secret = "secret";
		final JwtAuthorizationProperties jwtAuthorizationProperties = new JwtAuthorizationProperties();
		jwtAuthorizationProperties.setSecret(secret);

		final PrePostMethodSecurityConfiguration configuration = new PrePostMethodSecurityConfiguration();

		assertThat(configuration.jwtAuthorizationExtractionFilter(properties, jwtTokenUtil,
			webAuthenticationDetailsSource, applicationContext, objectMapper))
			.isNotNull().isInstanceOf(JwtAuthorizationExtractionFilter.class);
		assertThat(configuration.webAuthenticationDetailsSource()).isNotNull().isInstanceOf(
			WebAuthenticationDetailsSource.class);
		assertThat(configuration.jwtTokenUtil(jwtAuthorizationProperties)).isNotNull().hasFieldOrPropertyWithValue(
			"secret", secret.getBytes());
	}
}
