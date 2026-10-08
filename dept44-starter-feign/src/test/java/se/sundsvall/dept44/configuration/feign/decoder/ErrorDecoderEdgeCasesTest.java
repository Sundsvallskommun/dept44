package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.ProblemExceptionHandler;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static feign.Request.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * Responses the error decoders see in production but that are easy to miss: status codes without an
 * {@link org.springframework.http.HttpStatus} constant, bodies that can only be read once, and WSO2 rejecting a token.
 */
class ErrorDecoderEdgeCasesTest {

	private static final String INTEGRATION_NAME = "test-integration";

	@ParameterizedTest
	@ValueSource(ints = {
		499, 520, 598
	})
	void statusWithoutAnHttpStatusConstantGivesAProblem(final int status) {
		final var decoder = new ProblemErrorDecoder(INTEGRATION_NAME);

		final var withoutBody = decoder.decode("method", response(status, null, Map.of()));
		final var withBody = decoder.decode("method", response(status, "{\"title\":\"Odd\",\"status\":" + status + ",\"detail\":\"odd status\"}", Map.of()));

		assertThat(withoutBody).isInstanceOfAny(ClientProblem.class, ServerProblem.class).hasMessageContaining(String.valueOf(status));
		assertThat(withBody).isInstanceOfAny(ClientProblem.class, ServerProblem.class).hasMessageContaining("odd status");
		assertThat(((ThrowableProblem) withBody).getStatus()).isEqualTo(BAD_GATEWAY);
	}

	@Test
	void bodyThatCanOnlyBeReadOnceIsStillUsedForTheMessage() {
		final var decoder = new ProblemErrorDecoder(INTEGRATION_NAME);
		final var body = "{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"name is missing\"}";
		final var response = Response.builder()
			.status(400)
			.request(request())
			.headers(Map.of())
			.body(new ByteArrayInputStream(body.getBytes(UTF_8)), body.length())
			.build();

		assertThat(response.body().isRepeatable()).isFalse();
		assertThat(decoder.decode("method", response)).hasMessageContaining("name is missing");
	}

	@Test
	void rejectedTokenThatIsNotRetriedReachesTheCallerAsTheMappedProblem() {
		final var decoder = new ProblemErrorDecoder(INTEGRATION_NAME);
		final var response = response(401, null, Map.of("WWW-Authenticate", List.of("Bearer error=\"invalid_token\"")));

		final var exception = decoder.decode("method", response);
		final var handled = new ProblemExceptionHandler().handleException((Exception) exception, mock(HttpServletRequest.class));

		assertThat(exception).isInstanceOf(RetryableException.class);
		assertThat(handled.getStatusCode().value()).isEqualTo(BAD_GATEWAY.value());
	}

	private static Response response(final int status, final String body, final Map<String, Collection<String>> headers) {
		final var builder = Response.builder()
			.status(status)
			.request(request())
			.headers(headers);
		if (body != null) {
			builder.body(body, UTF_8);
		}
		return builder.build();
	}

	private static Request request() {
		return Request.create(GET, "http://localhost/api", Map.of(), null, UTF_8, new RequestTemplate());
	}
}
