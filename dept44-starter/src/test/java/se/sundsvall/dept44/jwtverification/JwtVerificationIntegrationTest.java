package se.sundsvall.dept44.jwtverification;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * A running server with the whole dept44 filter chain. The "gateway" verifier gets JWTs with a key id that is a base64
 * encoded certificate subject and a signature in standard Base64, as some issuers produce. A second verifier, "other",
 * checks another header against another issuer, except on the paths only the gateway calls.
 */
@SpringBootTest(classes = JwtVerificationIntegrationTest.TestApplication.class, webEnvironment = RANDOM_PORT, properties = {
	"dept44.jwt-verification.enabled=true",
	"dept44.jwt-verification.excluded-paths=/callback/**",
	"dept44.jwt-verification.verifiers.gateway.header-name=x-gateway-jwt",
	"dept44.jwt-verification.verifiers.other.header-name=x-other-jwt",
	"dept44.jwt-verification.verifiers.other.excluded-paths=/gateway-only/**"
})
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class JwtVerificationIntegrationTest {

	private static final String HEADER_NAME = "x-gateway-jwt";
	private static final String OTHER_HEADER_NAME = "x-other-jwt";
	private static final String OTHER_KEY_ID = "other-issuer-key";
	private static final String KEY_ID = Base64.getEncoder().withoutPadding().encodeToString("CN=*.example.se, OU=Support#832119384".getBytes());
	private static final KeyPair GATEWAY_KEY = generateKeyPair();
	private static final KeyPair OTHER_KEY = generateKeyPair();
	private static final KeyPair OTHER_ISSUER_KEY = generateKeyPair();
	private static final WireMockServer GATEWAY = new WireMockServer(options().dynamicPort());
	private static final WireMockServer OTHER_ISSUER = new WireMockServer(options().dynamicPort());

	static {
		GATEWAY.start();
		GATEWAY.stubFor(get("/jwks").willReturn(okJson(jwks(GATEWAY_KEY, KEY_ID))));
		OTHER_ISSUER.start();
		OTHER_ISSUER.stubFor(get("/keys").willReturn(okJson(jwks(OTHER_ISSUER_KEY, OTHER_KEY_ID))));
	}

	@Autowired
	private WebTestClient webTestClient;

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void gatewayProperties(final DynamicPropertyRegistry registry) {
		// Not "localhost": it can resolve to IPv6 first, where WireMock may not listen, which stalls the fetch of the keys
		registry.add("dept44.jwt-verification.verifiers.gateway.jwk-set-uris[0]", () -> "http://127.0.0.1:" + GATEWAY.port() + "/jwks");
		registry.add("dept44.jwt-verification.verifiers.other.jwk-set-uris[0]", () -> "http://127.0.0.1:" + OTHER_ISSUER.port() + "/keys");
	}

	@AfterAll
	static void stopIssuers() {
		GATEWAY.stop();
		OTHER_ISSUER.stop();
	}

	@Test
	void validAssertionWithStandardBase64Signature() {
		final var assertion = assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES));
		assertThat(assertion.substring(assertion.lastIndexOf('.') + 1)).containsAnyOf("+", "/", "=");

		webTestClient.get().uri("/things").header(HEADER_NAME, assertion).header(OTHER_HEADER_NAME, otherJwt())
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).isEqualTo("things");
	}

	@Test
	void expiredAssertion() {
		assertRejected(assertion(GATEWAY_KEY, KEY_ID, Instant.now().minus(15, MINUTES)), "JWT is not valid");
	}

	@Test
	void assertionSignedWithAnotherKey() {
		assertRejected(assertion(OTHER_KEY, KEY_ID, Instant.now().plus(15, MINUTES)), "JWT could not be verified");
	}

	@Test
	void malformedAssertion() {
		assertRejected("not-a-jwt", "JWT could not be verified");
	}

	@Test
	void missingAssertion() {
		assertRejected(null, "Missing JWT");
	}

	@Test
	void madeUpKeyIdsDoNotCostACallToTheGatewayEach() {
		final var valid = assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES));
		final var madeUp = assertion(GATEWAY_KEY, "made-up", Instant.now().plus(15, MINUTES));
		webTestClient.get().uri("/things").header(HEADER_NAME, valid).header(OTHER_HEADER_NAME, otherJwt()).exchange().expectStatus().isOk();
		final var fetchesBefore = GATEWAY.findAll(getRequestedFor(urlEqualTo("/jwks"))).size();

		// A key id the fetched keys don't hold gives 401; once the keys can't be fetched again yet, 503. How many fetches
		// are left depends on the tests before, so only the last status is certain
		final var statuses = new ArrayList<Integer>();
		for (var i = 0; i < 10; i++) {
			webTestClient.get().uri("/things").header(HEADER_NAME, madeUp).header(OTHER_HEADER_NAME, otherJwt())
				.exchange()
				.expectStatus().value(statuses::add)
				.expectBody()
				.jsonPath("$.title").isEqualTo("JWT could not be verified");
		}

		assertThat(statuses).containsOnly(401, 503).last().isEqualTo(503);
		assertThat(GATEWAY.findAll(getRequestedFor(urlEqualTo("/jwks"))).size() - fetchesBefore).isLessThanOrEqualTo(2);

		// The rate limit only concerns fetching keys: an assertion with a known key is still accepted
		webTestClient.get().uri("/things").header(HEADER_NAME, valid).header(OTHER_HEADER_NAME, otherJwt()).exchange().expectStatus().isOk();
	}

	@Test
	void bothVerifiersMustPass() {
		final var assertion = assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES));

		webTestClient.get().uri("/things").header(HEADER_NAME, assertion)
			.exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.title").isEqualTo("Missing JWT")
			.jsonPath("$.detail").isEqualTo("Header 'x-other-jwt' is missing");
	}

	@Test
	void eachVerifierUsesItsOwnKeys() {
		// Signed by the gateway's key, but presented in the other issuer's header
		final var gatewaySigned = assertion(GATEWAY_KEY, OTHER_KEY_ID, Instant.now().plus(15, MINUTES));

		webTestClient.get().uri("/things").header(HEADER_NAME, assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES))).header(OTHER_HEADER_NAME, gatewaySigned)
			.exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.title").isEqualTo("JWT could not be verified");
	}

	@Test
	void verifierExclusionSkipsOnlyThatVerifier() {
		webTestClient.get().uri("/gateway-only/hook").header(HEADER_NAME, assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES)))
			.exchange()
			.expectStatus().isOk();

		webTestClient.get().uri("/gateway-only/hook")
			.exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Header 'x-gateway-jwt' is missing");
	}

	/**
	 * Paths that match a built-in exclusion but are served by a controller whose first segment is a variable.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"/actuator/1/things", "/webjars/1/things", "/swagger-ui/1/things", "/api-docs/1/things"
	})
	void builtInPrefixDoesNotExcludeAController(final String path) {
		webTestClient.get().uri(path).header(OTHER_HEADER_NAME, otherJwt())
			.exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.detail").isEqualTo("Header 'x-gateway-jwt' is missing");
	}

	@Test
	void builtInPrefixedControllerIsServedWithAValidJwt() {
		webTestClient.get().uri("/actuator/1/things")
			.header(HEADER_NAME, assertion(GATEWAY_KEY, KEY_ID, Instant.now().plus(15, MINUTES)))
			.header(OTHER_HEADER_NAME, otherJwt())
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).isEqualTo("things of actuator");
	}

	@Test
	void unknownPathBelowABuiltInExclusionIsNotFound() {
		webTestClient.get().uri("/webjars/nothing-here.js")
			.exchange()
			.expectStatus().isNotFound();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/actuator/../things", "/actuator/%2e%2e/things", "/actuator;a=b/../things", "/callback/../things", "//things"
	})
	void pathTricksDoNotReachTheHandlerWithoutAnAssertion(final String path) {
		webTestClient.get().uri(URI.create("http://localhost:" + port + path))
			.exchange()
			.expectStatus().value(status -> assertThat(status).as(path).isGreaterThanOrEqualTo(400));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/", "/api-docs", "/api-docs.yaml", "/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html", "/actuator", "/actuator/health", "/actuator/info", "/callback/hook"
	})
	void excludedPathsNeedNoAssertion(final String path) {
		webTestClient.get().uri(path)
			.exchange()
			.expectStatus().value(status -> assertThat(status).as(path).isNotEqualTo(401));
	}

	private void assertRejected(final String assertion, final String title) {
		final var request = webTestClient.get().uri("/things").header(OTHER_HEADER_NAME, otherJwt());
		if (assertion != null) {
			request.header(HEADER_NAME, assertion);
		}

		request.exchange()
			.expectStatus().isUnauthorized()
			.expectHeader().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON)
			.expectBody()
			.jsonPath("$.status").isEqualTo(401)
			.jsonPath("$.title").isEqualTo(title);
	}

	/**
	 * Signs with the signature segment in standard Base64 with padding instead of base64url, as some issuers do.
	 */
	private static String assertion(final KeyPair key, final String keyId, final Instant expiresAt) {
		try {
			final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(keyId).build();
			final var claims = new JWTClaimsSet.Builder()
				.subject("user")
				.issuer("https://gateway.example")
				.issueTime(Date.from(expiresAt.minus(15, MINUTES)))
				.expirationTime(Date.from(expiresAt))
				.build();
			final var jwt = new SignedJWT(header, claims);
			jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));

			final var parts = jwt.serialize().split("\\.");
			final var signature = Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(parts[2]));
			return parts[0] + "." + parts[1] + "." + signature;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String otherJwt() {
		return assertion(OTHER_ISSUER_KEY, OTHER_KEY_ID, Instant.now().plus(5, MINUTES));
	}

	private static String jwks(final KeyPair key, final String keyId) {
		return new JWKSet(new RSAKey.Builder((RSAPublicKey) key.getPublic()).keyID(keyId).build()).toString();
	}

	private static KeyPair generateKeyPair() {
		try {
			final var generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		ThingsController thingsController() {
			return new ThingsController();
		}
	}

	@RestController
	static class ThingsController {

		@GetMapping(path = "/{municipalityId}/{id}/things", produces = TEXT_PLAIN_VALUE)
		String municipalityThings(@PathVariable("municipalityId") final String municipalityId) {
			return "things of " + municipalityId;
		}

		@GetMapping(path = "/things", produces = TEXT_PLAIN_VALUE)
		String things() {
			return "things";
		}

		@GetMapping(path = {
			"/callback/hook", "/gateway-only/hook"
		}, produces = TEXT_PLAIN_VALUE)
		String hook() {
			return "hook";
		}
	}
}
