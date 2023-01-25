package se.sundsvall.dept44.authorization.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class JwtAuthorizationPropertiesTest {

	@Test
	void verifyConfigurationAnnotation() {
		assertThat(getAnnotation(JwtAuthorizationProperties.class, Configuration.class)).isNotNull();
	}

	@Test
	void verifyConfigurationPropertiesAnnotation() {
		ConfigurationProperties annotation = getAnnotation(JwtAuthorizationProperties.class, ConfigurationProperties.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).isEqualTo("jwt.authorization");
	}

	@Test
	void verifyDefaultValues() {
		JwtAuthorizationProperties bean = new JwtAuthorizationProperties();

		assertThat(bean.getHeaderName()).isEqualTo("x-authorization-info");
		assertThat(bean.getSecret()).isNull();
	}

	@Test
	void verifySettersAndGetters() {
		final var headerName = "headerName";
		final var secret = "secret";

		JwtAuthorizationProperties bean = new JwtAuthorizationProperties();
		bean.setHeaderName(headerName);
		bean.setSecret(secret);

		assertThat(bean.getHeaderName()).isEqualTo(headerName);
		assertThat(bean.getSecret()).isEqualTo(secret);
	}
}
