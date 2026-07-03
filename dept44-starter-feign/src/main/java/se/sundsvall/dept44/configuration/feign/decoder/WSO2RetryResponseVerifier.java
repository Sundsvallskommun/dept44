package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Response;
import java.util.regex.Pattern;
import org.apache.hc.core5.http.HttpHeaders;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public class WSO2RetryResponseVerifier implements RetryResponseVerifier {

	/**
	 * Matches the RFC 6750 error code "invalid_token" in the WWW-Authenticate header. This is the only attribute that is
	 * stable across WSO2 versions (the error_description changed in APIM 4.0.0) and the only error that a token refresh can
	 * resolve. Retries are not triggered for "insufficient_scope" or "invalid_request", as a new token would not help.
	 */
	private static final String REGEXP = "error\\s*=\\s*\"?invalid_token\"?";
	private final Pattern pattern = Pattern.compile(REGEXP, Pattern.CASE_INSENSITIVE);

	@Override
	public boolean shouldReturnRetryableException(final Response response) {
		final var set = response.headers().get(HttpHeaders.WWW_AUTHENTICATE);
		return response.status() == UNAUTHORIZED.value() && nonNull(set) && set.stream().anyMatch(s -> pattern.matcher(s).find());
	}

	@Override
	public String getMessage() {
		return "Invalid token error";
	}
}
