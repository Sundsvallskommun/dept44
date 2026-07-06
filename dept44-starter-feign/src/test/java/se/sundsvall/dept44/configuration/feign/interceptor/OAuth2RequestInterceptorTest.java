package se.sundsvall.dept44.configuration.feign.interceptor;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import feign.Feign;
import feign.RequestInterceptor;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.okhttp.OkHttpClient;
import java.time.Duration;
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
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.test.util.ReflectionTestUtils;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	void initMocks() {
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
	void testRemoveTokenWhenFailedTokenMatchesCachedToken() {
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationMock);
		when(oAuth2AuthorizedClientServiceMock.<OAuth2AuthorizedClient>loadAuthorizedClient("registrationId", "anonymousUser")).thenReturn(authorizedClientMock);
		when(authorizedClientMock.getAccessToken()).thenReturn(accessTokenMock);
		when(accessTokenMock.getTokenValue()).thenReturn("tokenValue");

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);

			OAuth2RequestInterceptor interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			ReflectionTestUtils.setField(interceptor, "oAuth2AuthorizedClientService", oAuth2AuthorizedClientServiceMock);
			clearInvocations(clientRegistrationMock);

			interceptor.removeToken("Bearer tokenValue");
		}

		verify(oAuth2AuthorizedClientServiceMock).removeAuthorizedClient("registrationId", "anonymousUser");
	}

	@Test
	void testRemoveTokenWhenFailedTokenDiffersFromCachedToken() {
		// Simulates another thread having already refreshed the token: the cached token is no longer the one that
		// failed, so it must NOT be evicted.
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationMock);
		when(oAuth2AuthorizedClientServiceMock.<OAuth2AuthorizedClient>loadAuthorizedClient("registrationId", "anonymousUser")).thenReturn(authorizedClientMock);
		when(authorizedClientMock.getAccessToken()).thenReturn(accessTokenMock);
		when(accessTokenMock.getTokenValue()).thenReturn("freshTokenValue");

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);

			OAuth2RequestInterceptor interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			ReflectionTestUtils.setField(interceptor, "oAuth2AuthorizedClientService", oAuth2AuthorizedClientServiceMock);
			clearInvocations(clientRegistrationMock);

			interceptor.removeToken("Bearer staleTokenValue");
		}

		verify(oAuth2AuthorizedClientServiceMock, never()).removeAuthorizedClient(any(), any());
	}

	@Test
	void testRemoveTokenWhenNoCachedToken() {
		// Simulates another thread having already evicted the token: nothing to remove.
		when(clientRegistrationMock.getRegistrationId()).thenReturn("registrationId");
		when(clientRegistrationBuilderMock.scope(ArgumentMatchers.<HashSet<String>>any())).thenReturn(clientRegistrationBuilderMock);
		when(clientRegistrationBuilderMock.build()).thenReturn(clientRegistrationMock);
		when(oAuth2AuthorizedClientServiceMock.<OAuth2AuthorizedClient>loadAuthorizedClient("registrationId", "anonymousUser")).thenReturn(null);

		try (MockedStatic<ClientRegistration> regMock = Mockito.mockStatic(ClientRegistration.class)) {
			regMock.when(() -> ClientRegistration.withClientRegistration(any())).thenReturn(clientRegistrationBuilderMock);

			OAuth2RequestInterceptor interceptor = new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET);
			ReflectionTestUtils.setField(interceptor, "oAuth2AuthorizedClientService", oAuth2AuthorizedClientServiceMock);
			clearInvocations(clientRegistrationMock);

			interceptor.removeToken("Bearer anyToken");
		}

		verify(oAuth2AuthorizedClientServiceMock, never()).removeAuthorizedClient(any(), any());
	}

	@Test
	void testVerifyBodyContainsDeviceScope(WireMockRuntimeInfo wmRuntimeInfo) {
		stubTokenEndpoint(-1, 0);

		final var clientRegistration = createClientRegistration(wmRuntimeInfo.getHttpPort());
		final var interceptor = new OAuth2RequestInterceptor(clientRegistration, DEFAULT_SCOPESET);

		interceptor.apply(requestTemplateMock);

		com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlPathEqualTo("/token"))
			.withRequestBody(matching("^grant_type=client_credentials&scope=device_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")));
	}

	@Test
	void testVerifyBodyDoesNotContainsDeviceScope(WireMockRuntimeInfo wmRuntimeInfo) {
		stubTokenEndpoint(-1, 0);

		final var clientRegistration = createClientRegistration(wmRuntimeInfo.getHttpPort());
		final var interceptor = new OAuth2RequestInterceptor(clientRegistration, Set.of("scope1"));

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
		final var port = wmRuntimeInfo.getHttpPort();
		final var clientRegistration = createClientRegistration(port);

		final var customizer = FeignMultiCustomizer.create()
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

	@Test
	void testConstructorWithNullConnectTimeout() {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET, null, Duration.ofSeconds(1)))
			.withMessage("connectTimeout cannot be null");
	}

	@Test
	void testConstructorWithNullReadTimeout() {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new OAuth2RequestInterceptor(clientRegistrationMock, DEFAULT_SCOPESET, Duration.ofSeconds(1), null))
			.withMessage("readTimeout cannot be null");
	}

	@Test
	void testTokenFetchTimesOut(WireMockRuntimeInfo wmRuntimeInfo) {
		stubTokenEndpoint(3600, 2000);

		final var clientRegistration = createClientRegistration(wmRuntimeInfo.getHttpPort());
		final var interceptor = new OAuth2RequestInterceptor(clientRegistration, DEFAULT_SCOPESET, Duration.ofMillis(250), Duration.ofMillis(250));

		assertThatExceptionOfType(OAuth2AuthorizationException.class)
			.isThrownBy(() -> interceptor.apply(requestTemplateMock));
	}

	@Test
	void testTokenFetchWithinTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
		stubTokenEndpoint(3600, 0);

		final var clientRegistration = createClientRegistration(wmRuntimeInfo.getHttpPort());
		final var interceptor = new OAuth2RequestInterceptor(clientRegistration, DEFAULT_SCOPESET, Duration.ofSeconds(5), Duration.ofSeconds(5));

		interceptor.apply(requestTemplateMock);

		verify(requestTemplateMock).header(HttpHeaders.AUTHORIZATION, "Bearer MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3");
	}

	private static ClientRegistration createClientRegistration(final int port) {
		return ClientRegistration.withRegistrationId("test")
			.tokenUri("http://localhost:" + port + "/token")
			.clientSecret("secret")
			.clientName("name")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId("clientId")
			.build();
	}

	private static void stubTokenEndpoint(final int expiresInSeconds, final int fixedDelayInMilliseconds) {
		stubFor(post("/token")
			.willReturn(ok()
				.withHeader("Content-Type", "application/json")
				.withFixedDelay(fixedDelayInMilliseconds)
				.withBody("""
					{
						"access_token": "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3",
						"expires_in": %d,
						"refresh_token": "IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
						"scope": "whatever",
						"token_type": "bearer"
					}
					""".formatted(expiresInSeconds))));
	}

	public interface TestApi {
		@RequestLine("GET /test")
		String get();
	}
}
