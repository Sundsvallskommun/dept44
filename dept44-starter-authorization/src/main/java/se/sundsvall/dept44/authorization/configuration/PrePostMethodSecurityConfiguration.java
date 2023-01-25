package se.sundsvall.dept44.authorization.configuration;

import static org.springframework.util.Assert.hasText;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import se.sundsvall.dept44.authorization.JwtAuthorizationExtractionFilter;
import se.sundsvall.dept44.authorization.util.JwtTokenUtil;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class PrePostMethodSecurityConfiguration extends GlobalMethodSecurityConfiguration {

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
