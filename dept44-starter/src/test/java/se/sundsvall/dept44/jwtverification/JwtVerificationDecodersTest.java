package se.sundsvall.dept44.jwtverification;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import se.sundsvall.dept44.jwtverification.JwtVerificationProperties.Verifier;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The decoder a verifier gets, against issuers whose keys change, disappear or are not what the token claims.
 */
class JwtVerificationDecodersTest {

	private static final String JWKS = "/jwks";
	private static final String OUR_ISSUER = "https://login.microsoftonline.com/our-tenant/v2.0";

	private final WireMockServer internalGateway = new WireMockServer(options().dynamicPort());
	private final WireMockServer externalGateway = new WireMockServer(options().dynamicPort());
	private final RSAKey key = rsaKey("key-1");

	private JwtDecoder decoder;

	@BeforeEach
	void setUp() {
		internalGateway.start();
		externalGateway.start();
		decoder = JwtVerificationDecoders.create("gateway", verifier(List.of(SignatureAlgorithm.RS256), url(internalGateway))).jwtDecoder();
	}

	@AfterEach
	void tearDown() {
		internalGateway.stop();
		externalGateway.stop();
	}

	@Test
	void rotatedKeyIsPickedUp() throws Exception {
		publish(internalGateway, key);
		decoder.decode(signed(key));

		final var rotated = rsaKey("key-2");
		publish(internalGateway, rotated);

		assertThat(decoder.decode(signed(rotated)).getSubject()).isEqualTo("user");
	}

	@Test
	void fetchedKeysAreCachedWhenTheIssuerGoesDown() throws Exception {
		publish(internalGateway, key);
		decoder.decode(signed(key));

		internalGateway.stubFor(get(JWKS).willReturn(serviceUnavailable()));

		assertThat(decoder.decode(signed(key)).getSubject()).isEqualTo("user");
	}

	@Test
	void unavailableKeysAreNotReportedAsABadToken() throws Exception {
		internalGateway.stubFor(get(JWKS).willReturn(serviceUnavailable()));
		final var token = signed(key);

		assertThatExceptionOfType(JwtException.class)
			.isThrownBy(() -> decoder.decode(token))
			.isNotInstanceOf(BadJwtException.class);
	}

	@Test
	void unsignedTokenIsRejected() {
		publish(internalGateway, key);
		final var token = new PlainJWT(claims()).serialize();

		assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void tokenSignedWithPublicKeyAsSharedSecretIsRejected() throws Exception {
		publish(internalGateway, key);
		final var token = sign(new MACSigner(key.toRSAPublicKey().getEncoded()), JWSAlgorithm.HS256, key.getKeyID());

		assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void invalidJwkSetUriIsRefused() {
		final var verifier = verifier(List.of(SignatureAlgorithm.RS256), "not a uri");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> JwtVerificationDecoders.create("gateway", verifier))
			.withMessageContaining("verifiers.gateway.jwk-set-uris");
	}

	@Test
	void urlsAreFetchedInParallel() throws Exception {
		final var externalKey = rsaKey("external");
		internalGateway.stubFor(get(JWKS).willReturn(okJson(new JWKSet(key.toPublicJWK()).toString()).withFixedDelay(800)));
		externalGateway.stubFor(get(JWKS).willReturn(okJson(new JWKSet(externalKey.toPublicJWK()).toString()).withFixedDelay(800)));
		decoder = twoGatewayDecoder();

		final var started = System.nanoTime();
		decoder.decode(signed(key));

		// One after the other, the two fetches would take at least 1.6 seconds
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1400));
	}

	@Test
	void timingFollowsTheTimeouts() {
		final var timing = JwtVerificationDecoders.Timing.of(verifier(List.of(SignatureAlgorithm.RS256), "https://gateway.example/jwks"));

		// Two attempts of 5 s connect and 10 s read, plus a second
		assertThat(timing.fetch()).hasSeconds(31);
		assertThat(timing.cache()).hasMinutes(5);
		assertThat(timing.outage()).hasMinutes(50);
	}

	@Test
	void longTimeoutsLengthenTheCache() {
		final var verifier = new Verifier("x-gateway-jwt", List.of("https://gateway.example/jwks"), null, List.of(), List.of(), List.of(SignatureAlgorithm.RS256), Duration.ofMinutes(1), Duration.ofMinutes(2));

		final var timing = JwtVerificationDecoders.Timing.of(verifier);

		assertThat(timing.fetch()).isEqualTo(Duration.ofMinutes(6).plusSeconds(1));
		// Room for a refresh ahead of expiry and a fetch after it
		assertThat(timing.cache()).isGreaterThan(timing.fetch().multipliedBy(2));
	}

	@Test
	void tokenWithoutExpiryIsRejected() throws Exception {
		publish(internalGateway, key);
		final var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), new JWTClaimsSet.Builder().subject("user").build());
		jwt.sign(new RSASSASigner(key));
		final var token = jwt.serialize();

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void configuredIssuerIsAccepted() throws Exception {
		publish(internalGateway, key);
		decoder = issuerDecoder();

		assertThat(decoder.decode(signedWithIssuer(OUR_ISSUER)).getSubject()).isEqualTo("user");
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() throws Exception {
		// Same keys, as with Entra ID, where every tenant's tokens are signed with the same keys
		publish(internalGateway, key);
		decoder = issuerDecoder();
		final var token = signedWithIssuer("https://login.microsoftonline.com/another-tenant/v2.0");

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void tokenWithoutIssuerIsRejectedWhenOneIsConfigured() throws Exception {
		publish(internalGateway, key);
		decoder = issuerDecoder();
		final var token = signed(key);

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void anyIssuerIsAcceptedWhenNoneIsConfigured() throws Exception {
		publish(internalGateway, key);

		assertThat(decoder.decode(signedWithIssuer("https://anyone.example")).getSubject()).isEqualTo("user");
	}

	@Test
	void oneOfTheConfiguredAudiencesIsEnough() throws Exception {
		publish(internalGateway, key);
		decoder = audienceDecoder();

		assertThat(decoder.decode(signedFor(List.of("another-app", "api://our-app"))).getSubject()).isEqualTo("user");
	}

	@Test
	void tokenForAnotherAudienceIsRejected() throws Exception {
		publish(internalGateway, key);
		decoder = audienceDecoder();
		final var token = signedFor(List.of("another-app"));

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@Test
	void tokenWithoutAudienceIsRejectedWhenAudiencesAreConfigured() throws Exception {
		publish(internalGateway, key);
		decoder = audienceDecoder();
		final var token = signed(key);

		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"http://gateway.example/jwks", "ftp://gateway.example/jwks"
	})
	void plainHttpToAnotherHostIsRefused(final String uri) {
		final var verifier = verifier(List.of(SignatureAlgorithm.RS256), uri);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> JwtVerificationDecoders.create("gateway", verifier))
			.withMessageContaining("must use https");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"https://gateway.example/jwks", "http://localhost/jwks", "http://127.0.0.1:8080/jwks", "http://[::1]/jwks"
	})
	void httpsOrLocalHttpIsAccepted(final String uri) {
		final var verifier = verifier(List.of(SignatureAlgorithm.RS256), uri);

		assertThatNoException().isThrownBy(() -> JwtVerificationDecoders.create("gateway", verifier));
	}

	@Test
	void keysOfEveryUrlAreAccepted() throws Exception {
		final var externalKey = rsaKey("external");
		publish(internalGateway, key);
		publish(externalGateway, externalKey);
		decoder = twoGatewayDecoder();

		assertThat(decoder.decode(signed(key)).getSubject()).isEqualTo("user");
		assertThat(decoder.decode(signed(externalKey)).getSubject()).isEqualTo("user");
	}

	@Test
	void keysOfAllUrlsAreFetchedTogether() throws Exception {
		final var externalKey = rsaKey("external");
		publish(internalGateway, key);
		publish(externalGateway, externalKey);
		decoder = twoGatewayDecoder();

		decoder.decode(signed(key));
		decoder.decode(signed(externalKey));

		// One fetch of each, not a refetch for the second gateway's key
		assertThat(internalGateway.findAll(getRequestedFor(urlEqualTo(JWKS)))).hasSize(1);
		assertThat(externalGateway.findAll(getRequestedFor(urlEqualTo(JWKS)))).hasSize(1);
	}

	@Test
	void sameKeyFromTwoUrlsIsAccepted() throws Exception {
		publish(internalGateway, key);
		publish(externalGateway, key);
		decoder = twoGatewayDecoder();

		assertThat(decoder.decode(signed(key)).getSubject()).isEqualTo("user");
	}

	@Test
	void oneUrlDownStillAcceptsTheKeysOfTheOther() throws Exception {
		internalGateway.stubFor(get(JWKS).willReturn(serviceUnavailable()));
		final var externalKey = rsaKey("external");
		publish(externalGateway, externalKey);
		decoder = twoGatewayDecoder();

		assertThat(decoder.decode(signed(externalKey)).getSubject()).isEqualTo("user");
	}

	@Test
	void allUrlsDownIsNotReportedAsABadToken() throws Exception {
		internalGateway.stubFor(get(JWKS).willReturn(serviceUnavailable()));
		externalGateway.stubFor(get(JWKS).willReturn(serviceUnavailable()));
		decoder = twoGatewayDecoder();
		final var token = signed(key);

		assertThatExceptionOfType(JwtException.class)
			.isThrownBy(() -> decoder.decode(token))
			.isNotInstanceOf(BadJwtException.class);
	}

	@Test
	void onlyConfiguredAlgorithmsAreAccepted() throws Exception {
		final var ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec").generate();
		publish(internalGateway, key, ecKey);
		final var ecToken = sign(new ECDSASigner(ecKey), JWSAlgorithm.ES256, ecKey.getKeyID());

		assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode(ecToken));

		final var ecDecoder = JwtVerificationDecoders.create("gateway", verifier(List.of(SignatureAlgorithm.ES256, SignatureAlgorithm.RS256), url(internalGateway))).jwtDecoder();
		assertThat(ecDecoder.decode(ecToken).getSubject()).isEqualTo("user");
		assertThat(ecDecoder.decode(signed(key)).getSubject()).isEqualTo("user");
	}

	private JwtDecoder issuerDecoder() {
		final var verifier = new Verifier("x-jwt-ad", List.of(url(internalGateway)), OUR_ISSUER, List.of(), List.of(), List.of(SignatureAlgorithm.RS256), Duration.ofSeconds(5), Duration.ofSeconds(10));
		return JwtVerificationDecoders.create("ad", verifier).jwtDecoder();
	}

	private JwtDecoder audienceDecoder() {
		final var verifier = new Verifier("x-jwt-ad", List.of(url(internalGateway)), null, List.of("our-app-id", "api://our-app"), List.of(), List.of(SignatureAlgorithm.RS256),
			Duration.ofSeconds(5), Duration.ofSeconds(10));
		return JwtVerificationDecoders.create("ad", verifier).jwtDecoder();
	}

	private String signedFor(final List<String> audiences) throws Exception {
		final var claims = new JWTClaimsSet.Builder(claims()).audience(audiences).build();
		final var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private String signedWithIssuer(final String issuer) throws Exception {
		final var claims = new JWTClaimsSet.Builder(claims()).issuer(issuer).build();
		final var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private JwtDecoder twoGatewayDecoder() {
		return JwtVerificationDecoders.create("gateway", verifier(List.of(SignatureAlgorithm.RS256), url(internalGateway), url(externalGateway))).jwtDecoder();
	}

	private static void publish(final WireMockServer gateway, final JWK... keys) {
		final var publicKeys = List.of(keys).stream().map(JWK::toPublicJWK).toList();
		gateway.stubFor(get(JWKS).willReturn(okJson(new JWKSet(publicKeys).toString())));
	}

	private static String signed(final RSAKey key) throws Exception {
		return sign(new RSASSASigner(key), JWSAlgorithm.RS256, key.getKeyID());
	}

	private static String sign(final JWSSigner signer, final JWSAlgorithm algorithm, final String keyId) throws Exception {
		final var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(keyId).build(), claims());
		jwt.sign(signer);
		return jwt.serialize();
	}

	private static JWTClaimsSet claims() {
		return new JWTClaimsSet.Builder()
			.subject("user")
			.expirationTime(Date.from(Instant.now().plus(15, MINUTES)))
			.build();
	}

	/**
	 * Not "localhost": it can resolve to IPv6 first, where WireMock may not listen, which stalls the fetch of the keys.
	 */
	private static String url(final WireMockServer gateway) {
		return "http://127.0.0.1:" + gateway.port() + JWKS;
	}

	private static Verifier verifier(final List<SignatureAlgorithm> algorithms, final String... jwkSetUris) {
		return new Verifier("x-gateway-jwt", List.of(jwkSetUris), null, List.of(), List.of(), algorithms, Duration.ofSeconds(5), Duration.ofSeconds(10));
	}

	private static RSAKey rsaKey(final String keyId) {
		try {
			return new RSAKeyGenerator(2048).keyID(keyId).generate();
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
