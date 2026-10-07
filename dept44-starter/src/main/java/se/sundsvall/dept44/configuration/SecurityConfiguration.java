package se.sundsvall.dept44.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	@Order(0)
	SecurityFilterChain filterChain(final HttpSecurity http) {
		return http
			.csrf(CsrfConfigurer::disable) // Disable CSRF
			.securityMatcher("/**")
			.authorizeHttpRequests(authorizeHttpRequestsCustomizer -> authorizeHttpRequestsCustomizer.anyRequest().permitAll())
			.headers(headers -> headers.addObjectPostProcessor(new EagerHeaderWriting()))
			.build();
	}

	/**
	 * Makes Spring Security write its security headers before the request is handled, instead of when the response is
	 * committed or the filter chain returns. On an asynchronous response, such as a {@code StreamingResponseBody}, those
	 * two happen on different threads at the same time, and both write to the same header list, which is not
	 * thread-safe. The response then gets its security headers twice or a header without a name, which can fail the
	 * request.
	 */
	static final class EagerHeaderWriting implements ObjectPostProcessor<HeaderWriterFilter> {

		@Override
		public <O extends HeaderWriterFilter> O postProcess(final O filter) {
			filter.setShouldWriteHeadersEagerly(true);
			return filter;
		}
	}
}
