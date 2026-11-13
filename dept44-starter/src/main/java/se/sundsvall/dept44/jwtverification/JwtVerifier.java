package se.sundsvall.dept44.jwtverification;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.util.pattern.PathPattern;

/**
 * One JWT that {@link JwtVerificationFilter} verifies.
 *
 * @param name          the verifier's name in configuration, used in logs
 * @param headerName    the header that holds the JWT
 * @param excludedPaths paths this verifier does not check, on top of the ones no verifier checks
 * @param jwtDecoder    verifies the signature and expiry
 * @param keySource     the decoder's key source, closed with the filter to stop its refresh thread
 */
public record JwtVerifier(String name, String headerName, List<PathPattern> excludedPaths, JwtDecoder jwtDecoder, Closeable keySource) {

	private static final Logger LOGGER = LoggerFactory.getLogger(JwtVerifier.class);

	public JwtVerifier {
		excludedPaths = List.copyOf(excludedPaths);
	}

	boolean excludes(final PathContainer path) {
		return matches(excludedPaths, path);
	}

	static boolean matches(final List<PathPattern> patterns, final PathContainer path) {
		return patterns.stream().anyMatch(pattern -> pattern.matches(path));
	}

	void close() {
		try {
			keySource.close();
		} catch (final IOException e) {
			LOGGER.warn("JWT verifier '{}' could not close its key source", name, e);
		}
	}
}
