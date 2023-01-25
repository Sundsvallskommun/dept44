package se.sundsvall.dept44.configuration.resttemplate;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestTemplate;
import org.zalando.logbook.Logbook;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;


import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.Math.toIntExact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import se.sundsvall.dept44.requestid.RequestId;

class RestTemplateBuilderTest {

    private static final String BASE_URL = "https://example.com";
    private static final String USER_NAME = "userName";
    private static final String PASSWORD = "password";
    private static final String CLIENT_ID = "clientId";
    private static final String SECRET = "secret";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final Logbook LOGBOOK_MOCK = mock(Logbook.class);

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .proxyMode(true)
            .build();

    @Test
    void fieldsDefaultsToNull() {
        assertThat(new RestTemplateBuilder())
                .hasAllNullFieldsOrPropertiesExcept("connectTimeout", "readTimeout")
                .hasFieldOrPropertyWithValue("connectTimeout", Duration.ofSeconds(10))
                .hasFieldOrPropertyWithValue("readTimeout", Duration.ofSeconds(30));
    }

    @Test
    void baseUrlShouldApply() {
        RestTemplate restTemplate = new RestTemplateBuilder().withBaseUrl(BASE_URL).build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(BASE_URL.concat("/hello"))).andRespond(withSuccess());
        restTemplate.getForEntity("/hello", String.class);
        server.verify();
    }

    @Test
    void connectionTimeoutDefaultsTo10Seconds() {
        ClientHttpRequestFactory requestFactoryInterceptor = new RestTemplateBuilder().build().getRequestFactory();
        assertThat(requestFactoryInterceptor)
                .extracting("requestFactory")
                .extracting("requestConfig")
                .extracting("connectTimeout")
                .isEqualTo(10000);
    }

    @Test
    void connectionTimeoutCanBeConfigured() {
        ClientHttpRequestFactory requestFactoryInterceptor = new RestTemplateBuilder().withConnectTimeout(CONNECT_TIMEOUT).build().getRequestFactory();
        assertThat(requestFactoryInterceptor)
                .extracting("requestFactory")
                .extracting("requestConfig")
                .extracting("connectTimeout")
                .isEqualTo(toIntExact(CONNECT_TIMEOUT.toMillis()));
    }

    @Test
    void readTimeoutDefaultsTo30Seconds() {
        ClientHttpRequestFactory requestFactoryInterceptor = new RestTemplateBuilder().build().getRequestFactory();
        assertThat(requestFactoryInterceptor)
                .extracting("requestFactory")
                .extracting("requestConfig")
                .extracting("socketTimeout")
                .isEqualTo(30000);
    }

    @Test
    void readTimeoutCanBeConfigured() {
        ClientHttpRequestFactory requestFactoryInterceptor = new RestTemplateBuilder().withReadTimeout(READ_TIMEOUT).build().getRequestFactory();
        assertThat(requestFactoryInterceptor)
                .extracting("requestFactory")
                .extracting("requestConfig")
                .extracting("socketTimeout")
                .isEqualTo(toIntExact(READ_TIMEOUT.toMillis()));
    }

    @Test
    void basicAuthenticationShouldApply() {
        RestTemplate template = new RestTemplateBuilder().withBasicAuthentication(USER_NAME, PASSWORD).build();
        ClientHttpRequest request = createRequest(template);
        assertThat(request.getHeaders()).containsOnlyKeys(HttpHeaders.AUTHORIZATION);
        String base64String = Base64Utils.encodeToString(USER_NAME.concat(":").concat(PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertThat(request.getHeaders().get(HttpHeaders.AUTHORIZATION)).containsExactly("Basic ".concat(base64String));
    }

    @Test
    void oAuth2ClientRegistrationShouldApply() {
        RestTemplate template = new RestTemplateBuilder()
                .withBaseUrl(BASE_URL)
                .withOAuth2Client(createClientRegistration())
                .build();


        wm.stubFor(post("/token").withHost(equalTo("api-gateway.com")).willReturn(jsonResponse("""
                            {
                                "access_token": "%s",
                                "expires_in": -1,
                                "refresh_token": "IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
                                "scope": "create",
                                "token_type": "bearer"
                            }
                            """.formatted(ACCESS_TOKEN), 200)));

        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        server.expect(requestTo(BASE_URL.concat("/hello")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ".concat(ACCESS_TOKEN)))
                .andRespond(withSuccess());

        template.getForEntity("/hello", String.class);

        String base64String = Base64Utils.encodeToString(CLIENT_ID.concat(":").concat(SECRET).getBytes(StandardCharsets.UTF_8));
        wm.verify(postRequestedFor(urlEqualTo("/token"))
                .withHeader("Authorization", equalTo("Basic " + base64String)));
        server.verify();
    }

    @Test
    void logBookFieldShouldApply() {

        RestTemplateBuilder restTemplate = new RestTemplateBuilder()
                .withLogbook(LOGBOOK_MOCK);

        assertThat(restTemplate).hasFieldOrPropertyWithValue("logbook", LOGBOOK_MOCK);
    }

    @Test
    void oauth2AndBasicAuthShouldFail() {

        RestTemplateBuilder builder = new RestTemplateBuilder()
                .withOAuth2Client(createClientRegistration())
                .withBasicAuthentication(USER_NAME, PASSWORD);

        assertThat(assertThrows(IllegalStateException.class, () -> builder.build())).hasMessage("Basic Auth and OAuth2 cannot be used simultaneously");
    }

    @Test
    void restrictionsForBaseUrlShouldApply() {
        var builder = new RestTemplateBuilder();

        assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(null))).hasMessage("baseUrl may not be blank");
        assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(""))).hasMessage("baseUrl may not be blank");
        assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withBaseUrl(" "))).hasMessage("baseUrl may not be blank");
    }

    @Test
    void restrictionsForTimeOutsShouldApply() {
        var builder = new RestTemplateBuilder();

        assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withConnectTimeout(null))).hasMessage("connectTimeout may not be null");
        assertThat(assertThrows(IllegalArgumentException.class, () -> builder.withReadTimeout(null))).hasMessage("readTimeout may not be null");
    }

    @Test
    void requestIdHeaderIsAdded() {
        RequestId.init();

        RestTemplate restTemplate = new RestTemplateBuilder().withBaseUrl(BASE_URL).build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(BASE_URL.concat("/test")))
            .andExpect(header(RequestId.HEADER_NAME, matchesRegex("^[{]?[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}[}]?$")))
            .andRespond(withSuccess());
        restTemplate.getForEntity("/test", String.class);
        server.verify();
    }

    private ClientHttpRequest createRequest(RestTemplate template) {
        return ReflectionTestUtils.invokeMethod(template, "createRequest", URI.create("http://localhost"),
                HttpMethod.GET);
    }

    private ClientRegistration createClientRegistration() {
        return ClientRegistration.withRegistrationId("test")
                .tokenUri("http://api-gateway.com/token")
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}