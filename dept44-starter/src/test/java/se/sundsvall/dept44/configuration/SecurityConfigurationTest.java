package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
	void authorizeRequests() throws Exception {
		when(httpSecurityMock.securityMatcher(any(String[].class))).thenReturn(httpSecurityMock);
		when(httpSecurityMock.csrf(any())).thenReturn(httpSecurityMock);
		when(requestMatcherRegistryMock.anyRequest()).thenReturn(authorizedUrlMock);

		doAnswer(invocation -> {
			final Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> customizer = invocation.getArgument(0);
			customizer.customize(requestMatcherRegistryMock);
			return httpSecurityMock;
		}).when(httpSecurityMock).authorizeHttpRequests(any());

		when(authorizedUrlMock.permitAll()).thenReturn(requestMatcherRegistryMock);
		when(httpSecurityMock.build()).thenReturn(defaultSecurityFilterChain);

		final var chain = securityConfiguration.filterChain(httpSecurityMock);

		verify(httpSecurityMock).authorizeHttpRequests(any());
		verify(requestMatcherRegistryMock).anyRequest();
		verify(authorizedUrlMock).permitAll();
		verify(httpSecurityMock).build();
		assertThat(chain).isEqualTo(defaultSecurityFilterChain);
	}
}
