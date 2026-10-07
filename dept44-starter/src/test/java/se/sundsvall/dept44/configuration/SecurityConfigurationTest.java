package se.sundsvall.dept44.configuration;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import se.sundsvall.dept44.configuration.SecurityConfiguration.EagerHeaderWriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
	classes = SecurityConfiguration.class,
	webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ExtendWith(MockitoExtension.class)
class SecurityConfigurationTest {

	@Mock
	private HttpSecurity httpSecurityMock;

	@Mock
	private AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requestMatcherRegistryMock;

	@Mock
	private AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl authorizedUrlMock;

	@Mock
	private HeadersConfigurer<HttpSecurity> headersConfigurerMock;

	@Mock
	private DefaultSecurityFilterChain defaultSecurityFilterChain;

	@Autowired
	private SecurityFilterChain securityFilterChain;

	@InjectMocks
	private SecurityConfiguration securityConfiguration;

	@Test
	void securityFilterChain() {
		assertThat(securityFilterChain).isNotNull();
	}

	@Test
	void securityHeadersAreWrittenBeforeTheRequestIsHandled() throws Exception {
		final var headerWriterFilter = securityFilterChain.getFilters().stream()
			.filter(HeaderWriterFilter.class::isInstance)
			.findFirst()
			.orElseThrow();
		final var seenByHandler = new AtomicReference<String>();

		headerWriterFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
			(request, response) -> seenByHandler.set(((HttpServletResponse) response).getHeader("X-Content-Type-Options")));

		assertThat(seenByHandler).hasValue("nosniff");
	}

	@Test
	void authorizeRequests() {
		when(httpSecurityMock.securityMatcher(any(String[].class))).thenReturn(httpSecurityMock);
		when(httpSecurityMock.csrf(any())).thenReturn(httpSecurityMock);
		when(requestMatcherRegistryMock.anyRequest()).thenReturn(authorizedUrlMock);

		doAnswer(invocation -> {
			final Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> customizer = invocation.getArgument(0);
			customizer.customize(requestMatcherRegistryMock);
			return httpSecurityMock;
		}).when(httpSecurityMock).authorizeHttpRequests(any());

		doAnswer(invocation -> {
			final Customizer<HeadersConfigurer<HttpSecurity>> customizer = invocation.getArgument(0);
			customizer.customize(headersConfigurerMock);
			return httpSecurityMock;
		}).when(httpSecurityMock).headers(any());

		when(authorizedUrlMock.permitAll()).thenReturn(requestMatcherRegistryMock);
		when(httpSecurityMock.build()).thenReturn(defaultSecurityFilterChain);

		final var chain = securityConfiguration.filterChain(httpSecurityMock);

		verify(httpSecurityMock).authorizeHttpRequests(any());
		verify(requestMatcherRegistryMock).anyRequest();
		verify(authorizedUrlMock).permitAll();
		verify(headersConfigurerMock).addObjectPostProcessor(isA(EagerHeaderWriting.class));
		verify(httpSecurityMock).build();
		assertThat(chain).isEqualTo(defaultSecurityFilterChain);
	}
}
