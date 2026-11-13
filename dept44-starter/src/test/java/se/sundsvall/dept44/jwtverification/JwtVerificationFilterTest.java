package se.sundsvall.dept44.jwtverification;

import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static se.sundsvall.dept44.jwtverification.JwtVerificationFilter.DETAIL_KEYS_RATE_LIMITED;
import static se.sundsvall.dept44.jwtverification.JwtVerificationFilter.DETAIL_KEYS_UNAVAILABLE;
import static se.sundsvall.dept44.jwtverification.JwtVerificationFilter.TITLE_INVALID;
import static se.sundsvall.dept44.jwtverification.JwtVerificationFilter.TITLE_MISSING;
import static se.sundsvall.dept44.jwtverification.JwtVerificationFilter.TITLE_UNVERIFIED;

@ExtendWith(MockitoExtension.class)
class JwtVerificationFilterTest {

	private static final String HEADER_NAME = "x-gateway-jwt";
	private static final String OTHER_HEADER_NAME = "x-other-jwt";
	private static final String JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2lnbmF0dXJl/+==";
	private static final String OTHER_JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJvdGhlciJ9.b3RoZXI";
	private static final String MISSING_DETAIL = "Header 'x-gateway-jwt' is missing";
	private static final Closeable NO_KEY_SOURCE = () -> {};

	@Mock
	private JwtDecoder jwtDecoderMock;

	@Mock
	private JwtDecoder otherJwtDecoderMock;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final MockHttpServletResponse response = new MockHttpServletResponse();
	private final MockFilterChain chain = new MockFilterChain();

	private JwtVerificationFilter filter;
	private boolean builtInEndpoint = true;

	@BeforeEach
	void setUp() {
		filter = new JwtVerificationFilter(patterns("/", "/actuator/**"), request -> builtInEndpoint, patterns("/callback/**"), List.of(verifier()), jsonMapper);
	}

	@Test
	void validJwtContinuesTheChain() throws Exception {
		when(jwtDecoderMock.decode(JWT)).thenReturn(mock(Jwt.class));

		filter.doFilter(request("/things", JWT), response, chain);

		verify(jwtDecoderMock).decode(JWT);
		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/", "/actuator", "/actuator/health", "/callback/x/y"
	})
	void excludedPathIsNotVerified(final String path) throws Exception {
		filter.doFilter(request(path, null), response, chain);

		verifyNoInteractions(jwtDecoderMock);
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void builtInPathNotServedByItsEndpointIsVerified() throws Exception {
		builtInEndpoint = false;

		filter.doFilter(request("/actuator/1/things", null), response, chain);

		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@Test
	void configuredExclusionNeedsNoEndpointCheck() throws Exception {
		builtInEndpoint = false;

		filter.doFilter(request("/callback/x", null), response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void servletPathIsNotDecodedAgain() throws Exception {
		// What the container gives for "/%2561ctuator/health": decoded once, so still not "/actuator/health"
		filter.doFilter(request("/%61ctuator/health", null), response, chain);

		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@Test
	void problemIsWrittenAsUtf8() throws Exception {
		when(jwtDecoderMock.decode(anyString())).thenThrow(new BadJwtException("Ogiltig signatur: åäö"));

		filter.doFilter(request("/things", JWT), response, chain);

		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		assertRejected(TITLE_UNVERIFIED, "Ogiltig signatur: åäö");
	}

	@Test
	void destroyClosesTheKeySources() throws Exception {
		final var keySource = mock(Closeable.class);
		final var failingKeySource = mock(Closeable.class);
		doThrow(new IOException("closed")).when(failingKeySource).close();
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(),
			List.of(new JwtVerifier("a", HEADER_NAME, List.of(), jwtDecoderMock, failingKeySource), new JwtVerifier("b", OTHER_HEADER_NAME, List.of(), otherJwtDecoderMock, keySource)), jsonMapper);

		filter.destroy();

		verify(failingKeySource).close();
		verify(keySource).close();
	}

	@Test
	void exclusionsMatchThePathWithinTheApplication() throws Exception {
		final var request = request("/actuator/health", null);
		request.setContextPath("/app");
		request.setRequestURI("/app/actuator/health");

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void exclusionsMatchServletPathAndPathInfoTogether() throws Exception {
		final var request = request("/actuator", null);
		request.setPathInfo("/health");

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/actuator/../things", "/actuator/%2e%2e/things", "/actuator;a=b/../things"
	})
	void rawRequestUriCanNotFakeAnExclusion(final String requestUri) throws Exception {
		final var request = request("/things", null);
		request.setRequestURI(requestUri);

		filter.doFilter(request, response, chain);

		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@Test
	void rootIsExcludedOnlyByExactMatch() throws Exception {
		filter.doFilter(request("/things", null), response, chain);

		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"", " "
	})
	void blankJwtIsRejected(final String jwt) throws Exception {
		filter.doFilter(request("/things", jwt), response, chain);

		verifyNoInteractions(jwtDecoderMock);
		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@Test
	void expiredJwtIsRejected() throws Exception {
		when(jwtDecoderMock.decode(anyString())).thenThrow(new JwtValidationException("Jwt expired at 2026-11-03T12:00:00Z", List.of(new OAuth2Error("invalid_token"))));

		filter.doFilter(request("/things", JWT), response, chain);

		assertRejected(TITLE_INVALID, "Jwt expired at 2026-11-03T12:00:00Z");
	}

	@Test
	void unverifiableJwtIsRejected() throws Exception {
		when(jwtDecoderMock.decode(anyString())).thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));

		filter.doFilter(request("/things", JWT), response, chain);

		assertRejected(TITLE_UNVERIFIED, "Signed JWT rejected: Invalid signature");
	}

	@Test
	void unavailableKeysGiveServiceUnavailableWithoutTheCause() throws Exception {
		when(jwtDecoderMock.decode(anyString())).thenThrow(new JwtException("Couldn't retrieve remote JWK set: https://gateway/jwks"));

		filter.doFilter(request("/things", JWT), response, chain);

		assertProblem(503, TITLE_UNVERIFIED, DETAIL_KEYS_UNAVAILABLE.formatted(HEADER_NAME));
	}

	@Test
	void rateLimitedFetchGivesServiceUnavailable() throws Exception {
		when(jwtDecoderMock.decode(anyString())).thenThrow(new JwtException("Rate limit reached", new RateLimitReachedException()));

		filter.doFilter(request("/things", JWT), response, chain);

		assertProblem(503, TITLE_UNVERIFIED, DETAIL_KEYS_RATE_LIMITED.formatted(HEADER_NAME));
	}

	@Test
	void everyVerifierMustPass() throws Exception {
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(), List.of(verifier(), otherVerifier()), jsonMapper);
		when(jwtDecoderMock.decode(JWT)).thenReturn(mock(Jwt.class));
		when(otherJwtDecoderMock.decode(OTHER_JWT)).thenReturn(mock(Jwt.class));
		final var request = request("/things", JWT);
		request.addHeader(OTHER_HEADER_NAME, OTHER_JWT);

		filter.doFilter(request, response, chain);

		verify(jwtDecoderMock).decode(JWT);
		verify(otherJwtDecoderMock).decode(OTHER_JWT);
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void oneFailingVerifierRejects() throws Exception {
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(), List.of(verifier(), otherVerifier()), jsonMapper);
		when(jwtDecoderMock.decode(JWT)).thenReturn(mock(Jwt.class));

		filter.doFilter(request("/things", JWT), response, chain);

		assertRejected(TITLE_MISSING, "Header 'x-other-jwt' is missing");
	}

	@Test
	void rejectionByTheFirstVerifierSkipsTheRest() throws Exception {
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(), List.of(verifier(), otherVerifier()), jsonMapper);

		filter.doFilter(request("/things", null), response, chain);

		verifyNoInteractions(otherJwtDecoderMock);
		assertRejected(TITLE_MISSING, MISSING_DETAIL);
	}

	@Test
	void verifierExclusionsSkipOnlyThatVerifier() throws Exception {
		final var other = new JwtVerifier("other", OTHER_HEADER_NAME, patterns("/gateway-only/**"), otherJwtDecoderMock, NO_KEY_SOURCE);
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(), List.of(verifier(), other), jsonMapper);
		when(jwtDecoderMock.decode(JWT)).thenReturn(mock(Jwt.class));

		filter.doFilter(request("/gateway-only/x", JWT), response, chain);

		verifyNoInteractions(otherJwtDecoderMock);
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void verifierExclusionsDoNotSkipTheOthers() throws Exception {
		final var excluding = new JwtVerifier("gateway", HEADER_NAME, patterns("/callback/**"), jwtDecoderMock, NO_KEY_SOURCE);
		filter = new JwtVerificationFilter(List.of(), request -> true, List.of(), List.of(excluding, otherVerifier()), jsonMapper);

		filter.doFilter(request("/callback/x", null), response, chain);

		verifyNoInteractions(jwtDecoderMock);
		assertRejected(TITLE_MISSING, "Header 'x-other-jwt' is missing");
	}

	private JwtVerifier verifier() {
		return new JwtVerifier("gateway", HEADER_NAME, List.of(), jwtDecoderMock, NO_KEY_SOURCE);
	}

	private JwtVerifier otherVerifier() {
		return new JwtVerifier("other", OTHER_HEADER_NAME, List.of(), otherJwtDecoderMock, NO_KEY_SOURCE);
	}

	private void assertRejected(final String title, final String detail) throws Exception {
		assertProblem(401, title, detail);
	}

	private void assertProblem(final int status, final String title, final String detail) throws Exception {
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(response.getContentType()).isEqualTo(APPLICATION_PROBLEM_JSON_VALUE + ";charset=UTF-8");

		final var body = jsonMapper.readTree(response.getContentAsString());
		assertThat(body.get("status").asInt()).isEqualTo(status);
		assertThat(body.get("title").asString()).isEqualTo(title);
		assertThat(body.get("detail").asString()).isEqualTo(detail);
		assertThat(response.getContentAsString()).doesNotContain(JWT, OTHER_JWT);
	}

	private static List<PathPattern> patterns(final String... patterns) {
		return List.of(patterns).stream().map(PathPatternParser.defaultInstance::parse).toList();
	}

	private static MockHttpServletRequest request(final String path, final String jwt) {
		final var request = new MockHttpServletRequest("GET", path);
		request.setServletPath(path);
		if (jwt != null) {
			request.addHeader(HEADER_NAME, jwt);
		}
		return request;
	}
}
