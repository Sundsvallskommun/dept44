package se.sundsvall.dept44.configuration.feign.interceptor;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.Feign;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.okhttp.OkHttpClient;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.util.ReflectionTestUtils;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

@WireMockTest
class OAuth2RequestInterceptorTest {

	private static final Set<String> DEFAULT_SCOPESET = new HashSet<>(Arrays.asList("device_" + UUID.randomUUID()));

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Mock
	private RequestTemplate requestTemplateMock;

	@Mock
	private AuthorizedClientServiceOAuth2AuthorizedClientManager clientManagerMock;

	@Mock
	private OAuth2AuthorizedClient authorizedClientMock;

	@Mock
	private InMemoryOAuth2AuthorizedClientService oAuth2AuthorizedClientServiceMock;

	@Mock
	private OAuth2AccessToken accessTokenMock;

	@Mock
	private ClientRegistration.Builder clientRegistrationBuilderMock;

	@Captor
	private ArgumentCaptor<HashSet<String>> scopeCaptor;

	@BeforeEach
	public void initMocks() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testImplements() {
		assertThat(RequestInterceptor.class).isAssignableFrom(OAuth2RequestInterceptor.class);
	}

	@Test
	void testConstructorWithDeviceScope() {
		ClientRegistration clientRegistrationWithScopeMock = mock(ClientRegistration.class);
		when(clientRegistrationWithScopeMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationMock.getScopes()).thenReturn(Set.of("scope1", "scope2"));
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationWithScopeMock);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);
			var interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			assertThat(interceptor)
				.isNotNull()
				.hasFieldOrPropertyWithValue("clientRegistration", clientRegistrationWithScopeMock);
		}

		verify(clientRegistrationBuilderMock).scope(scopeCaptor.capture());
		Pattern pattern = Pattern.compile("device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$", Pattern.MULTILINE);
		assertThat(scopeCaptor.getValue()).anyMatch(s -> pattern.matcher(s).find());
		assertThat(scopeCaptor.getValue()).containsAll(Set.of("scope1", "scope2"));
	}

	@Test
	void testConstructorWithEmptySet() {
		ClientRegistration clientRegistrationWithScopeMock = mock(ClientRegistration.class);
		when(clientRegistrationWithScopeMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationMock.getScopes()).thenReturn(Set.of("scope1", "scope2"));
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationWithScopeMock);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);
			var interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, emptySet());
			assertThat(interceptor)
				.isNotNull()
				.hasFieldOrPropertyWithValue("clientRegistration", clientRegistrationWithScopeMock);
		}

		verify(clientRegistrationBuilderMock).scope(scopeCaptor.capture());
		Pattern pattern = Pattern.compile("device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$", Pattern.MULTILINE);
		assertThat(scopeCaptor.getValue()).noneMatch(s -> pattern.matcher(s).find());
		assertThat(scopeCaptor.getValue()).containsAll(Set.of("scope1", "scope2"));
	}

	@Test
	void testConstructorWithNullSet() {
		ClientRegistration clientRegistrationWithScopeMock = mock(ClientRegistration.class);
		when(clientRegistrationWithScopeMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationMock.getScopes()).thenReturn(Set.of("scope1", "scope2"));
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationWithScopeMock);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);
			var interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, null);
			assertThat(interceptor)
				.isNotNull()
				.hasFieldOrPropertyWithValue("clientRegistration", clientRegistrationWithScopeMock);
		}

		verify(clientRegistrationBuilderMock).scope(scopeCaptor.capture());
		Pattern pattern = Pattern.compile("device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$", Pattern.MULTILINE);
		assertThat(scopeCaptor.getValue()).noneMatch(s -> pattern.matcher(s).find());
		assertThat(scopeCaptor.getValue()).containsAll(Set.of("scope1", "scope2"));
	}

	@Test
	void testConstructorWithNull() {
		assertThat(assertThrows(IllegalArgumentException.class, () -> new OAuth2RequestInterceptor(null, null)))
			.hasMessage("clientRegistration cannot be null");
	}

	@Test
	void testApplyWithNoAuthorizedClient() {
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationMock);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);
			final var oAuth2RequestInterceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			assertThat(assertThrows(IllegalArgumentException.class, () -> oAuth2RequestInterceptor.apply(requestTemplateMock)))
				.hasMessage("authorizedClient cannot be null");
		}
	}

	@Test
	void testApplyWithAuthorizedClient() {
		ClientRegistration clientRegistrationWithScopeMock = mock(ClientRegistration.class);
		when(clientRegistrationWithScopeMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationWithScopeMock);
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientManagerMock.authorize(any())).thenReturn(authorizedClientMock);
		when(authorizedClientMock.getAccessToken()).thenReturn(accessTokenMock);
		when(accessTokenMock.getTokenValue()).thenReturn("tokenValue");

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);

			OAuth2RequestInterceptor interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			ReflectionTestUtils.setField(interceptor, "authorizedClientManager", clientManagerMock);

			clearInvocations(clientRegistrationWithScopeMock);

			interceptor.apply(requestTemplateMock);
		}

		verify(requestTemplateMock).header(HttpHeaders.AUTHORIZATION, "Bearer tokenValue");
		verify(clientRegistrationWithScopeMock).getRegistrationId();
	}

	@Test
	void testRemoveToken() {
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationMock);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);

			OAuth2RequestInterceptor interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			ReflectionTestUtils.setField(interceptor, "oAuth2AuthorizedClientService", oAuth2AuthorizedClientServiceMock);
			clearInvocations(clientRegistrationMock);

			interceptor.removeToken();
		}

		verify(clientRegistrationMock).getRegistrationId();
		verify(oAuth2AuthorizedClientServiceMock).removeAuthorizedClient("registrationId", "anonymousUser");
	}

	@Test
	void testVerifyBodyContainsDeviceScope(WireMockRuntimeInfo wmRuntimeInfo) {
		stubFor(post("/token")
			.willReturn(ok()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
						"access_token": "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3",
						"expires_in": -1,
						"refresh_token": "IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
						"scope": "whatever",
						"token_type": "bearer"
					}
					""")));

		int port = wmRuntimeInfo.getHttpPort();
		var clientRegistration = ClientRegistration.withRegistrationId("test")
			.tokenUri("http://localhost:" + port + "/token")
			.clientSecret("secret")
			.clientName("name")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("clientId")
			.build();

		var interceptor = new OAuth2RequestInterceptor(clientRegistration, DEFAULT_SCOPESET);

		interceptor.apply(requestTemplateMock);

		com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlPathEqualTo("/token"))
			.withRequestBody(matching("^grant_type=client_credentials&scope=device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")));
	}

	@Test
	void testVerifyBodyDoesNotContainsDeviceScope(WireMockRuntimeInfo wmRuntimeInfo) {
		stubFor(post("/token")
			.willReturn(ok()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
						"access_token": "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3",
						"expires_in": -1,
						"refresh_token": "IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
						"scope": "whatever",
						"token_type": "bearer"
					}
					""")));

		int port = wmRuntimeInfo.getHttpPort();
		var clientRegistration = ClientRegistration.withRegistrationId("test")
			.tokenUri("http://localhost:" + port + "/token")
			.clientSecret("secret")
			.clientName("name")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("clientId")
			.build();

		var interceptor = new OAuth2RequestInterceptor(clientRegistration, Set.of("scope1"));

		interceptor.apply(requestTemplateMock);

		com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlPathEqualTo("/token"))
			.withRequestBody(matching("^grant_type=client_credentials&scope=scope1$")));
	}

	@Test
	void testVerifyTokenRenewal(WireMockRuntimeInfo wmRuntimeInfo) {

		// ===== Setup token server stubs =====
		var token1 = "firstToken";
		var token2 = "secondToken";
		var tokenBody = """
			{
				"access_token": "%s",
				"expires_in": 3600,
				"refresh_token": "IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
				"scope": "whatever",
				"token_type": "bearer"
			}
			""";

		stubFor(post("/token")
			.inScenario("Retry")
			.whenScenarioStateIs(STARTED)
			.willReturn(ok()
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody(String.format(tokenBody, token1)))
			.willSetStateTo("second call"));

		stubFor(post("/token")
			.inScenario("Retry")
			.whenScenarioStateIs("second call")
			.willReturn(ok()
				.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.withBody(String.format(tokenBody, token2))));

		// ===== Setup API stubs =====
		stubFor(get("/test")
			.inScenario("get")
			.whenScenarioStateIs(STARTED)
			.willReturn(unauthorized()
				.withHeader("Content-Type", "text/plain")
				.withHeader("www-authenticate", "\"OAuth2 realm=\"WSO2 API Manager\", error=\"invalid_token\", error_description=\"The access token expired\"")
				.withBody("ERROR!"))
			.willSetStateTo("second call"));

		stubFor(get("/test")
			.inScenario("get")
			.whenScenarioStateIs("second call")
			.willReturn(ok()
				.withHeader("Content-Type", "text/plain")
				.withBody("successful")));

		// ===== Create feign client =====
		int port = wmRuntimeInfo.getHttpPort();
		var clientRegistration = ClientRegistration.withRegistrationId("test")
			.tokenUri("http://localhost:" + port + "/token")
			.clientSecret("secret")
			.clientName("name")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("clientId")
			.build();

		var customizer = FeignMultiCustomizer.create()
			.withErrorDecoder(new ProblemErrorDecoder("name"))
			.withRequestTimeoutsInSeconds(5, 5)
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistration)
			.composeCustomizersToOne();

		var builder = Feign.builder()
			.client(new OkHttpClient());
		customizer.customize(builder);

		var target = builder.target(TestApi.class, "http://localhost:" + port + "/");

		// ===== Make call =====
		var result = target.get();

		// ===== Assertions =====
		assertThat(result).isEqualTo("successful");

		com.github.tomakehurst.wiremock.client.WireMock.verify(2, postRequestedFor(urlPathEqualTo("/token"))
			.withRequestBody(matching("^grant_type=client_credentials&scope=device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")));

		com.github.tomakehurst.wiremock.client.WireMock.verify(2, getRequestedFor(urlPathEqualTo("/test")));
		var requests = findAll(getRequestedFor(urlPathEqualTo("/test")));
		assertThat(requests.get(0).header("Authorization").values()).containsExactly("Bearer " + token1);
		assertThat(requests.get(1).header("Authorization").values()).containsExactly("Bearer " + token2);
	}

	public interface TestApi {
		@RequestLine("GET /test")
		String get();
	}
}
