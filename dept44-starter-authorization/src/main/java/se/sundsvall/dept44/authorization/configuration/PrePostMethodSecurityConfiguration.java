package se.sundsvall.dept44.authorization.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;

import static org.springframework.util.Assert.hasText;

@Configuration
@EnableMethodSecurity
public class PrePostMethodSecurityConfiguration {

	@Bean
	JwtAuthorizationExtractionFilter jwtAuthorizationExtractionFilter(
		JwtAuthorizationProperties properties,
		JwtTokenUtil jwtTokenUtil,
		WebAuthenticationDetailsSource webAuthenticationDetailsSource,
		ApplicationContext applicationContext,
		ObjectMapper objectMapper) {

		return new JwtAuthorizationExtractionFilter(properties, jwtTokenUtil, webAuthenticationDetailsSource, applicationContext, objectMapper);
	}

	@Bean
	JwtTokenUtil jwtTokenUtil(JwtAuthorizationProperties properties) {
		hasText(properties.getSecret(), "Required property 'jwt.authorization.secret' must be present in service properties");
		return new JwtTokenUtil(properties.getSecret());
	}

	@Bean
	WebAuthenticationDetailsSource webAuthenticationDetailsSource() {
		return new WebAuthenticationDetailsSource();
	}
}
