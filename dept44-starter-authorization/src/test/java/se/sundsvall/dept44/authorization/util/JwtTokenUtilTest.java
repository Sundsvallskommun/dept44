package se.sundsvall.dept44.authorization.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;

@ExtendWith(ResourceLoaderExtension.class)
class JwtTokenUtilTest {

	private final String secret = "df6b9fb15cfdbb7527be5a8a6e39f39e572c8ddb943fbc79a943438e9d3d85ebfc2ccf9e0eccd9346026c0b6876e0e01556fe56f135582c05fbdbb505d46755a";
	private final String expired_message_regex_match = "^JWT expired (\\d+) milliseconds ago at 1971-01-01T00:00:00.000Z. Current time: (\\d{4}-\\d{2}-\\d{1,2}T\\d{1,2}:\\d{1,2}:\\d{1,2}.\\d{0,3})Z. Allowed clock skew: 0 milliseconds.$";

	/**
	 * Token with the following content, verified with secret
	 * 'df6b9fb15cfdbb7527be5a8a6e39f39e572c8ddb943fbc79a943438e9d3d85ebfc2ccf9e0eccd9346026c0b6876e0e01556fe56f135582c05fbdbb505d46755a'
	 *
	 * Header:
	 *
	 * <pre>
	 * {
	 * 		"alg": "HS512",
	 * 		"typ": "JWT"
	 * }
	 * </pre>
	 *
	 * Body:
	 *
	 * <pre>
	 * {
	 * 		"sub": "userName",
	 * 		"exp": "<date in Epoch format>",
	 * 		"roles": {
	 * 			"READ": ["CATEGORY_1"],
	 * 			"WRITE": ["CATEGORY_1", "CATEGORY_2"]
	 * 		}
	 * }
	 * </pre>
	 */

	@Test
	void verifyComponentAnnotation() {
		assertThat(AnnotationUtils.getAnnotation(JwtTokenUtil.class, Component.class)).isNotNull();
	}

	@Test
	void constructorWithNull() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new JwtTokenUtil(null));

		assertThat(exception.getMessage()).isEqualTo("String containing secret must be present");
	}

	@Test
	void constructorWithSecret() {
		final var secret = "secretKey";
		final JwtTokenUtil util = new JwtTokenUtil(secret);

		assertThat(util).isNotNull().hasFieldOrPropertyWithValue("secret", secret.getBytes());
	}

	@Test
	void getUsername(@Load("valid_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final var userName = util.getUsernameFromToken(jwt);

		assertThat(userName).isEqualTo("userName");
	}

	@Test
	void getExpirationDate(@Load("valid_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final var expireDate = util.getExpirationDateFromToken(jwt);

		assertThat(expireDate).isEqualTo(Instant.parse("4096-01-01T00:00:00Z"));
	}

	@Test
	void getRoles(@Load("valid_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final var roles = util.getRolesFromToken(jwt);

		assertThat(roles)
			.extracting(GenericGrantedAuthority::toString)
			.containsExactlyInAnyOrder(
				"GenericGrantedAuthority [role=READ, accesses=[\"CATEGORY_1\"]]",
				"GenericGrantedAuthority [role=WRITE, accesses=[\"CATEGORY_1\",\"CATEGORY_2\"]]");
	}

	@Test
	void processExpiredJwt(@Load("expired_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final ExpiredJwtException exception = assertThrows(ExpiredJwtException.class, () -> util.getUsernameFromToken(jwt));

		assertThat(exception.getMessage()).matches(expired_message_regex_match);
	}

	@Test
	void processInvalidSignatureJwt(@Load("invalid_signature_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final SignatureException exception = assertThrows(SignatureException.class, () -> util.getUsernameFromToken(jwt));

		assertThat(exception.getMessage()).isEqualTo("JWT signature does not match locally computed signature. JWT validity cannot be asserted and should not be trusted.");
	}

	@Test
	void processMalformedJwt(@Load("malformed_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil(secret);
		final MalformedJwtException exception = assertThrows(MalformedJwtException.class, () -> util.getUsernameFromToken(jwt));

		assertThat(exception.getMessage()).isEqualTo("Compact JWT strings MUST always have a Base64Url protected header per https://tools.ietf.org/html/rfc7519#section-7.2 (steps 2-4).");
	}

	@Test
	void utilCreationWithWeakKey(@Load("weak_secret_jwt.txt") String jwt) {
		final JwtTokenUtil util = new JwtTokenUtil("weak_key");
		final WeakKeyException exception = assertThrows(WeakKeyException.class, () -> util.getUsernameFromToken(jwt));

		assertThat(exception.getMessage()).isEqualTo(
			"The specified key byte array is 64 bits which is not secure enough for any JWT HMAC-SHA algorithm.  The JWT JWA Specification (RFC 7518, Section 3.2) states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be greater than or equal to the hash output size).  Consider using the Jwts.SIG.HS256.key() builder (or HS384.key() or HS512.key()) to create a key guaranteed to be secure enough for your preferred HMAC-SHA algorithm.  See https://tools.ietf.org/html/rfc7518#section-3.2 for more information.");
	}
}
