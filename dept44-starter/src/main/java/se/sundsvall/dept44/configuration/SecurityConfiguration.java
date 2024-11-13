package se.sundsvall.dept44.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(authorizeHttpRequestsCustomizer -> authorizeHttpRequestsCustomizer.anyRequest()
				.permitAll())
			.build();
	}

	@Bean
	WebSecurityCustomizer webSecurityCustomizer() {
		return web -> web.ignoring().anyRequest();
	}
}
