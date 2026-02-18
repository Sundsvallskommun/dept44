package se.sundsvall.dept44.authorization.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.util.Assert.hasText;

@Configuration
@EnableMethodSecurity
public class PrePostMethodSecurityConfiguration {

	@Bean
	JwtAuthorizationExtractionFilter jwtAuthorizationExtractionFilter(
		final JwtAuthorizationProperties properties,
		final JwtTokenUtil jwtTokenUtil,
		final WebAuthenticationDetailsSource webAuthenticationDetailsSource,
		final ApplicationContext applicationContext,
		final JsonMapper jsonMapper) {

		return new JwtAuthorizationExtractionFilter(properties, jwtTokenUtil, webAuthenticationDetailsSource, applicationContext, jsonMapper);
	}

	@Bean
	JwtTokenUtil jwtTokenUtil(final JwtAuthorizationProperties properties) {
		hasText(properties.getSecret(), "Required property 'jwt.authorization.secret' must be present in service properties");
		return new JwtTokenUtil(properties.getSecret());
	}

	@Bean
	WebAuthenticationDetailsSource webAuthenticationDetailsSource() {
		return new WebAuthenticationDetailsSource();
	}
}
