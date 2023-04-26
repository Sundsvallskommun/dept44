package se.sundsvall.dept44.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@SpringBootTest(classes = { SecurityConfiguration.class, SecurityConfigurationTest.CustomWebConfiguration.class })
class SecurityConfigurationTest {

    /*
     * Custom configuration to ensure that there is a HandlerMappingIntrospector bean, as it is
     * required for the SecurityConfiguration to work properly, and since we don't set up the web
     * context in this test
     */
    @Configuration
    static class CustomWebConfiguration {

        @Bean
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }

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
    void test_authorizeRequests() throws Exception {
        when(httpSecurityMock.authorizeHttpRequests()).thenReturn(requestMatcherRegistryMock);
        when(requestMatcherRegistryMock.requestMatchers(any(EndpointRequest.EndpointRequestMatcher.class)))
            .thenReturn(authorizedUrlMock);
        when(authorizedUrlMock.permitAll()).thenReturn(requestMatcherRegistryMock);
        when(requestMatcherRegistryMock.and()).thenReturn(httpSecurityMock);
        when(httpSecurityMock.build()).thenReturn(defaultSecurityFilterChain);

        final var securityFilterChain = securityConfiguration.filterChain(httpSecurityMock);

        verify(httpSecurityMock).authorizeHttpRequests();
        verify(requestMatcherRegistryMock).requestMatchers(any(EndpointRequest.EndpointRequestMatcher.class));
        verify(authorizedUrlMock).permitAll();
        verify(requestMatcherRegistryMock).and();
        verify(httpSecurityMock).build();
        assertThat(securityFilterChain).isEqualTo(defaultSecurityFilterChain);
    }
}
