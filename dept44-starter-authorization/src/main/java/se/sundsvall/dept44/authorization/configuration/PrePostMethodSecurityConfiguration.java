package se.sundsvall.dept44.authorization.configuration;

import static org.springframework.util.Assert.hasText;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;

@Configuration
@EnableMethodSecurity
public class PrePostMethodSecurityConfiguration {

	@Bean
	public JwtAuthorizationExtractionFilter jwtAuthorizationExtractionFilter() {
		return new JwtAuthorizationExtractionFilter();
	}
	
	@Bean
	public JwtTokenUtil jwtTokenUtil(JwtAuthorizationProperties properties) {
		hasText(properties.getSecret(), "Required property 'jwt.authorization.secret' must be present in service properties");
		return new JwtTokenUtil(properties.getSecret());
	}
	
	@Bean
	public WebAuthenticationDetailsSource webAuthenticationDetailsSource() {
		return new WebAuthenticationDetailsSource();
	}
}
