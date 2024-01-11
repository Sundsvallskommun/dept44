package se.sundsvall.dept44.configuration.feign.decoder;

import static feign.Request.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.dept44.configuration.feign.decoder.WSO2RetryResponseVerifierTest.WSO2_TOKEN_EXPIRE_HEADER_ERROR;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;

@ExtendWith(ResourceLoaderExtension.class)
class ProblemErrorDecoderTest {

	@Test
	void testImplements() {
		assertThat(ErrorDecoder.class).isAssignableFrom(ProblemErrorDecoder.class);
		assertThat(AbstractErrorDecoder.class).isAssignableFrom(ProblemErrorDecoder.class);
	}

	@Test
	void decodeMinimalProblem(@Load("minimalProblem.json") String errorBody) {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(errorBody, 418, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage(
			"Bad Gateway: XXX error: {status=418 I'm a teapot, title=You do not have enough credit.}");
	}

	@Test
	void decodeProblem(@Load("problem.json") String errorBody) {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(errorBody, 418, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage(
			"Bad Gateway: XXX error: {detail=Your current balance is 30, but that costs 50., status=418 I'm a teapot, title=You do not have enough credit.}");
	}

	@Test
	void decodeConstraintViolationProblem(@Load("constraintViolationProblem.json") String errorBody) {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(errorBody, 400, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage(
			"Bad Gateway: XXX error: {detail=property1: property1 must be valid!, property2: property2 is also invalid!!, status=400 Bad Request, title=Constraint Violation}");
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderForErrorMessages")
	void errorDecoderForErrorMessages(String body, int httpStatus, String expectedMessage) {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(body, httpStatus, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage(expectedMessage);
	}

	@Test
	void errorDecoderWhenBypassResponseCodesAreSet() {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX", List.of(400, 401, 404, 418));
		final var response = buildErrorResponse(null, 404, null); // statusCode exists in bypassList.

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Not Found: XXX error: {status=404 Not Found, title=Not Found}");
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderReturnsCorrectThrowableType")
	void errorDecoderReturnsCorrectThrowableType(int httpStatus, Class<ThrowableProblem> type) {

		// Setup
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var errorResponse = buildErrorResponse("""
			{
				"title": "this is a title",
				"detail": "this is a detail"
			}
			""", httpStatus, null);

		// Execute
		final var exception = errorDecoder.decode("test", errorResponse);

		// Verify
		assertThat(exception).isInstanceOf(type);
	}

	@Test
	void errorDecoderReturnsRetryableExceptionOnWSO2TokenExpire() {
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var errorResponse = buildErrorResponse("Error", 401, Map.of("www-authenticate", Set.of(WSO2_TOKEN_EXPIRE_HEADER_ERROR)));

		final var exception = errorDecoder.decode("test", errorResponse);

		assertThat(exception).isInstanceOf(RetryableException.class);

	}

	@Test
	void errorDecoderReturnRetryableException() {
		final var retryResponseVerifierMock = Mockito.mock(RetryResponseVerifier.class);
		final var errorDecoder = new ProblemErrorDecoder("XXX", emptyList(), retryResponseVerifierMock);
		final var errorResponse = buildErrorResponse("Error", 500, null);

		when(retryResponseVerifierMock.shouldReturnRetryableException(any())).thenReturn(true);
		when(retryResponseVerifierMock.getMessage()).thenReturn("Special message");

		final var exception = errorDecoder.decode("test", errorResponse);

		verify(retryResponseVerifierMock).shouldReturnRetryableException(same(errorResponse));
		verify(retryResponseVerifierMock).getMessage();
		assertThat(exception).isInstanceOf(RetryableException.class);
		assertThat(exception.getMessage()).isEqualTo("Special message");
		assertThat(exception.getCause()).isInstanceOf(ServerProblem.class);
	}

	private static Response buildErrorResponse(String errorBody, int httpStatus, Map<String, Collection<String>> headers) {
		return Response.builder()
			.body(errorBody, UTF_8)
			.request(Request.create(GET, "/api", emptyMap(), null, UTF_8, new RequestTemplate()))
			.status(httpStatus)
			.headers(headers)
			.build();
	}

	private static Stream<Arguments> toErrorDecoderReturnsCorrectThrowableType() {
		return Stream.of(
			Arguments.of(400, ClientProblem.class),
			Arguments.of(401, ClientProblem.class),
			Arguments.of(404, ClientProblem.class),
			Arguments.of(418, ClientProblem.class),
			Arguments.of(422, ClientProblem.class),
			Arguments.of(500, ServerProblem.class),
			Arguments.of(501, ServerProblem.class),
			Arguments.of(502, ServerProblem.class),
			Arguments.of(100, Problem.class),
			Arguments.of(302, Problem.class));
	}

	private static Stream<Arguments> toErrorDecoderForErrorMessages() {
		return Stream.of(
			Arguments.of("<unknown message structure></unknown message structure>", 418, "Bad Gateway: XXX error: {status=418 I'm a teapot, title=Unknown error}"),
			Arguments.of(null, 401, "Bad Gateway: XXX error: {status=401 Unauthorized, title=Unauthorized}"),
			Arguments.of("  ", 404, "Bad Gateway: XXX error: {status=404 Not Found, title=Not Found}"));
	}
}
