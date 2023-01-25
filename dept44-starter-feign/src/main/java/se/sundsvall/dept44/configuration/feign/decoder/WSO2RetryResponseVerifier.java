package se.sundsvall.dept44.configuration.feign.decoder;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.regex.Pattern;

import org.apache.http.HttpHeaders;

import feign.Response;

public class WSO2RetryResponseVerifier implements RetryResponseVerifier {

	private static final String REGEXP = "realm=\"WSO2 API Manager\".*error=\"invalid_token\".*error_description=\"The access token expired\"";
	private final Pattern pattern = Pattern.compile(REGEXP, Pattern.MULTILINE);

	@Override
	public boolean shouldReturnRetryableException(final Response response) {
		final var set = response.headers().get(HttpHeaders.WWW_AUTHENTICATE);
		return response.status() == UNAUTHORIZED.value() && nonNull(set) && set.stream().anyMatch(s -> pattern.matcher(s).find());
	}

	@Override
	public String getMessage() {
		return "WSO2 Token expire error";
	}
}
