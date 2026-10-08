package se.sundsvall.dept44.authorization.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.authorization.model.GenericGrantedAuthority;
import tools.jackson.databind.json.JsonMapper;

import static java.util.Collections.emptyMap;
import static org.springframework.util.Assert.hasText;

@Component
public class JwtTokenUtil implements Serializable {

	private static final long serialVersionUID = -2550185165626007488L;
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final byte[] secret;

	public JwtTokenUtil(final String secret) {
		hasText(secret, "String containing secret must be present");
		this.secret = secret.getBytes(Charset.defaultCharset());
	}

	/**
	 * Retrieves username from jwt-token.
	 *
	 * @param  token jwt-token containing authorization information
	 * @return       string with data mapped from tag with name 'sub' in token, or null if data is missing
	 */
	public String getUsernameFromToken(final String token) {
		return getClaimFromToken(token, Claims::getSubject);
	}

	/**
	 * Retrieves roles from jwt-token.
	 *
	 * @param  token containing authorization information
	 * @return       a collection with data mapped from tag with name 'roles' in token, or empty collection if data is
	 *               missing
	 */
	public Collection<GenericGrantedAuthority> getRolesFromToken(final String token) {
		final Map<?, ?> roles = getAllClaimsFromToken(token).get("roles", Map.class);
		return Optional.ofNullable(roles).orElse(emptyMap()).entrySet().stream()
			.map(entry -> GenericGrantedAuthority.create(String.valueOf(entry.getKey()), toJson(entry.getValue())))
			.toList();
	}

	/**
	 * The accesses of a role are queried with json-path, so they are handed on as JSON. The claim has already been parsed
	 * into lists and maps; their {@code toString()} is not JSON (strings lose their quotes and get split on commas). A
	 * string is handed on as it is, since an issuer may send the accesses as JSON inside a string.
	 */
	private static String toJson(final Object accesses) {
		return switch (accesses) {
			case null -> null;
			case final String text -> text;
			default -> JSON_MAPPER.writeValueAsString(accesses);
		};
	}

	/**
	 * Retrieves expiration date from jwt-token.
	 *
	 * @param  token containing authorization information
	 * @return       date with data mapped from tag with name 'exp' in token, or null if data is missing
	 */
	public Date getExpirationDateFromToken(final String token) {
		return getClaimFromToken(token, Claims::getExpiration);
	}

	/**
	 * Retrieves claim from jwt-token.
	 *
	 * @param  <T>            the type of claim
	 * @param  token          containing authorization information
	 * @param  claimsResolver to use to fetch and return claim
	 * @return                claim of type defined in sent in claimsResolver, or null if data is missing in token
	 */
	public <T> T getClaimFromToken(final String token, final Function<Claims, T> claimsResolver) {
		final Claims claims = getAllClaimsFromToken(token);
		return claimsResolver.apply(claims);
	}

	private Claims getAllClaimsFromToken(final String token) {
		return Jwts.parser()
			.verifyWith(Keys.hmacShaKeyFor(secret))
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
