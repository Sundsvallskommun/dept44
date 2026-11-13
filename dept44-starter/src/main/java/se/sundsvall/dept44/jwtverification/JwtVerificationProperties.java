package se.sundsvall.dept44.jwtverification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.validation.annotation.Validated;

/**
 * Verification of signed JWTs that incoming requests carry in headers, e.g. one that an API gateway adds to every
 * request it forwards.
 * <p>
 * The properties are only bound when verification is enabled, so the required ones are required then and only then.
 *
 * @param enabled       whether incoming requests are verified, default {@code false}
 * @param excludedPaths path patterns (Spring {@code PathPattern} syntax) that no verifier checks, in addition to the
 *                      built-in ones (index page, OpenAPI documents, Swagger UI and actuator). Like controller
 *                      mappings, they are relative to {@code spring.mvc.servlet.path}. Unlike the built-in ones, they
 *                      are matched on the path alone, whichever handler serves it
 * @param verifiers     the JWTs to verify, by name. A map rather than a list, so that a profile can override one
 *                      verifier's settings without repeating the others. Every verifier must pass
 */
@Validated
@ConfigurationProperties(prefix = JwtVerificationProperties.PREFIX)
public record JwtVerificationProperties(
	boolean enabled,
	@DefaultValue List<String> excludedPaths,
	@DefaultValue @NotEmpty Map<String, @Valid @NotNull Verifier> verifiers) {

	public static final String PREFIX = "dept44.jwt-verification";

	/**
	 * One JWT to verify.
	 *
	 * @param headerName     the header that holds the JWT
	 * @param jwkSetUris     the issuer's JWKS endpoints, https only. Their keys are merged into one key set, e.g. for two
	 *                       gateways that sign with different keys
	 * @param issuer         the {@code iss} the JWT must have, if set. Needed when the keys are not the issuer's own: Entra
	 *                       ID signs the tokens of every tenant with the same keys, so only {@code iss} tells a token
	 *                       from your organisation apart, e.g. {@code https://login.microsoftonline.com/<tenant-id>/v2.0}
	 * @param audiences      if set, the JWT's {@code aud} must contain one of them, e.g. the service's application id and
	 *                       its {@code api://} URI. Not set: any audience is accepted
	 * @param excludedPaths  path patterns that this verifier does not check, in addition to the global ones
	 * @param algorithms     the accepted signature algorithms, default {@code RS256}
	 * @param connectTimeout connect timeout when fetching the keys, default 5 seconds. Set for the worst case: the keys
	 *                       are refreshed ahead of expiry, so a slow endpoint rarely holds up a request
	 * @param readTimeout    read timeout when fetching the keys, default 10 seconds
	 */
	public record Verifier(
		@NotBlank String headerName,
		@NotEmpty List<@NotBlank String> jwkSetUris,
		String issuer,
		@DefaultValue List<@NotBlank String> audiences,
		@DefaultValue List<String> excludedPaths,
		@DefaultValue("RS256") @NotEmpty List<@NotNull SignatureAlgorithm> algorithms,
		@DefaultValue("5s") @NotNull @DurationMin(millis = 100) @DurationMax(minutes = 5) Duration connectTimeout,
		@DefaultValue("10s") @NotNull @DurationMin(millis = 100) @DurationMax(minutes = 5) Duration readTimeout) {
	}
}
