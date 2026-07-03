package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static feign.Request.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class WSO2RetryResponseVerifierTest {

	public static final String WSO2_TOKEN_EXPIRE_HEADER_ERROR = "OAuth2 realm=\"WSO2 API Manager\", error=\"invalid_token\", error_description=\"The access token expired\"";
	private final WSO2RetryResponseVerifier verifier = new WSO2RetryResponseVerifier();

	@ParameterizedTest
	@ValueSource(strings = {
		// WSO2 APIM 3.x format
		"OAuth2 realm=\"WSO2 API Manager\", error=\"invalid_token\", error_description=\"The access token expired\"",
		// WSO2 APIM 4.x format (error_description changed in carbon-apimgt PR #10293)
		"OAuth2 realm=\"WSO2 API Manager\", error=\"invalid_token\", error_description=\"The provided token is invalid\"",
		// RFC 6750 compliant Bearer scheme
		"Bearer realm=\"example\", error=\"invalid_token\", error_description=\"The access token expired\"",
		// Attribute order is unspecified
		"error=\"invalid_token\", realm=\"WSO2 API Manager\"",
		// Token form (unquoted)
		"Bearer error=invalid_token",
		// Whitespace around equals sign
		"Bearer error = \"invalid_token\"",
		// Case-insensitive
		"Bearer ERROR=\"INVALID_TOKEN\""
	})
	void shouldReturnRetryableExceptionForInvalidToken(final String wwwAuthenticateHeader) {
		final var response = response(401, wwwAuthenticateHeader);

		assertThat(verifier.shouldReturnRetryableException(response)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		// A new token would not gain more scopes
		"Bearer realm=\"example\", error=\"insufficient_scope\", error_description=\"The request requires higher privileges\"",
		// A new token would not fix a malformed request
		"Bearer realm=\"example\", error=\"invalid_request\", error_description=\"The request is missing a required parameter\"",
		"Some other error"
	})
	void shouldNotReturnRetryableExceptionForNonTokenErrors(final String wwwAuthenticateHeader) {
		final var response = response(401, wwwAuthenticateHeader);

		assertThat(verifier.shouldReturnRetryableException(response)).isFalse();
	}

	@Test
	void shouldNotReturnRetryableExceptionWrongStatusCode() {
		final var response = response(402, WSO2_TOKEN_EXPIRE_HEADER_ERROR);

		assertThat(verifier.shouldReturnRetryableException(response)).isFalse();
	}

	@Test
	void shouldNotReturnRetryableExceptionWhenHeaderIsMissing() {
		final var response = Response.builder()
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(401)
			.headers(emptyMap())
			.build();

		assertThat(verifier.shouldReturnRetryableException(response)).isFalse();
	}

	@Test
	void getMessage() {
		assertThat(verifier.getMessage()).isEqualTo("Invalid token error");
	}

	private Response response(final int status, final String wwwAuthenticateHeader) {
		return Response.builder()
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(status)
			.headers(Map.of("www-authenticate", Set.of(wwwAuthenticateHeader)))
			.build();
	}
}
