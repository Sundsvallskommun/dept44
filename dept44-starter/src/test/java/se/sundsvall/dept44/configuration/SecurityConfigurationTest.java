package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = SecurityConfiguration.class)
class SecurityConfigurationTest {

    @Mock
    private HttpSecurity httpSecurityMock;

    @Mock
    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry expressionInterceptUrlRegistryMock;

    @Mock
    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl authorizedUrlMock;

    @Mock
    private CsrfConfigurer<HttpSecurity> csrfConfigurerMock;

    @Mock
    private DefaultSecurityFilterChain defaultSecurityFilterChain;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private WebSecurityCustomizer webSecurityCustomizer;

    @InjectMocks
    private SecurityConfiguration securityConfiguration;

    @Test
    void test_securityFilterChain() {
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void test_webSecurityCustomizer() {
        assertThat(webSecurityCustomizer).isNotNull();
    }

    @Test
    void test_authorizeRequests() throws Exception{
        when(httpSecurityMock.authorizeRequests()).thenReturn(expressionInterceptUrlRegistryMock);
        when(expressionInterceptUrlRegistryMock.requestMatchers(any())).thenReturn(authorizedUrlMock);
        when(authorizedUrlMock.permitAll()).thenReturn(expressionInterceptUrlRegistryMock);
        when(expressionInterceptUrlRegistryMock.and()).thenReturn(httpSecurityMock);
        when(httpSecurityMock.csrf()).thenReturn(csrfConfigurerMock);
        when(csrfConfigurerMock.disable()).thenReturn(httpSecurityMock);
        when(httpSecurityMock.build()).thenReturn(defaultSecurityFilterChain);

        final var securityFilterChain = securityConfiguration.filterChain(httpSecurityMock);

        verify(httpSecurityMock).authorizeRequests();
        verify(expressionInterceptUrlRegistryMock).requestMatchers(any(EndpointRequest.EndpointRequestMatcher.class));
        verify(authorizedUrlMock).permitAll();
        verify(expressionInterceptUrlRegistryMock).and();
        verify(httpSecurityMock).csrf();
        verify(csrfConfigurerMock).disable();
        verify(httpSecurityMock).build();
        assertThat(securityFilterChain).isEqualTo(defaultSecurityFilterChain);
    }
}
