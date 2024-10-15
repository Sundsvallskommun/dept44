package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static feign.Request.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class WSO2RetryResponseVerifierTest {

	public static final String WSO2_TOKEN_EXPIRE_HEADER_ERROR = "OAuth2 realm=\"WSO2 API Manager\", error=\"invalid_token\", error_description=\"The access token expired\"";
	private final WSO2RetryResponseVerifier verifier = new WSO2RetryResponseVerifier();

	@Test
	void shouldReturnRetryableException() {
		var response = Response.builder()
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(401)
			.headers(Map.of("www-authenticate", Set.of(WSO2_TOKEN_EXPIRE_HEADER_ERROR)))
			.build();

		assertThat(verifier.shouldReturnRetryableException(response)).isTrue();
	}

	@Test
	void shouldNotReturnRetryableExceptionWrongStatusCode() {
		var response = Response.builder()
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(402)
			.headers(Map.of("www-authenticate", Set.of(WSO2_TOKEN_EXPIRE_HEADER_ERROR)))
			.build();

		assertThat(verifier.shouldReturnRetryableException(response)).isFalse();
	}

	@Test
	void shouldNotReturnRetryableExceptionWrongHeader() {
		var response = Response.builder()
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(401)
			.headers(Map.of("www-authenticate", Set.of("Some other error")))
			.build();

		assertThat(verifier.shouldReturnRetryableException(response)).isFalse();
	}

	@Test
	void getMessage() {
		assertThat(verifier.getMessage()).isEqualTo("WSO2 Token expire error");
	}
}
