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

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
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
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;

@ExtendWith(ResourceLoaderExtension.class)
class ProblemErrorDecoderTest {

	private static Response buildErrorResponse(final String errorBody, final int httpStatus, final Map<String, Collection<String>> headers) {
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
			Arguments.of(409, ClientProblem.class),
			Arguments.of(410, ClientProblem.class),
			Arguments.of(500, ServerProblem.class),
			Arguments.of(501, ServerProblem.class),
			Arguments.of(502, ServerProblem.class),
			Arguments.of(100, Problem.class),
			Arguments.of(302, Problem.class));
	}

	private static Stream<Arguments> toErrorDecoderForErrorMessages() {
		return Stream.of(
			Arguments.of("<unknown message structure></unknown message structure>", 409, "Bad Gateway: XXX error: {status=409 Conflict, title=Unknown error}"),
			Arguments.of(null, 401, "Bad Gateway: XXX error: {status=401 Unauthorized, title=Unauthorized}"),
			Arguments.of("  ", 404, "Bad Gateway: XXX error: {status=404 Not Found, title=Not Found}"));
	}

	@Test
	void testImplements() {
		assertThat(ErrorDecoder.class).isAssignableFrom(ProblemErrorDecoder.class);
		assertThat(AbstractErrorDecoder.class).isAssignableFrom(ProblemErrorDecoder.class);
	}

	private static Stream<Arguments> toDecodeProblemArguments() {
		return Stream.of(
			Arguments.of("minimalProblem.json", 402, "Bad Gateway: XXX error: {status=402 Payment Required, title=You do not have enough credit.}"),
			Arguments.of("problem.json", 402, "Bad Gateway: XXX error: {detail=Your current balance is 30, but that costs 50., status=402 Payment Required, title=You do not have enough credit.}"),
			Arguments.of("constraintViolationProblem.json", 400, "Bad Gateway: XXX error: {detail=property1: property1 must be valid!, property2: property2 is also invalid!!, status=400 Bad Request, title=Constraint Violation}"));
	}

	@ParameterizedTest
	@MethodSource("toDecodeProblemArguments")
	void decodeProblem(final String resourceFile, final int httpStatus, final String expectedMessage) throws Exception {

		// Arrange
		final var errorBody = new String(getClass().getResourceAsStream("/" + resourceFile).readAllBytes(), UTF_8);
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(errorBody, httpStatus, null);

		// Act
		final var exception = errorDecoder.decode("test", response);

		// Assert
		assertThat(exception)
			.isExactlyInstanceOf(ClientProblem.class)
			.hasMessage(expectedMessage);
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderForErrorMessages")
	void errorDecoderForErrorMessages(final String body, final int httpStatus, final String expectedMessage) {

		// Arrange
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var response = buildErrorResponse(body, httpStatus, null);

		// Act
		final var exception = errorDecoder.decode("test", response);

		// Assert
		assertThat(exception).hasMessage(expectedMessage);
	}

	@Test
	void errorDecoderWhenBypassResponseCodesAreSet() {

		// Arrange
		final var errorDecoder = new ProblemErrorDecoder("XXX", List.of(400, 401, 404, 409));
		final var response = buildErrorResponse(null, 404, null); // statusCode exists in bypassList.

		// Act
		final var exception = errorDecoder.decode("test", response);

		// Assert
		assertThat(exception)
			.isExactlyInstanceOf(ClientProblem.class)
			.hasMessage("Not Found: XXX error: {status=404 Not Found, title=Not Found}");
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderReturnsCorrectThrowableType")
	void errorDecoderReturnsCorrectThrowableType(final int httpStatus, final Class<ThrowableProblem> type) {

		// Arrange
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var errorResponse = buildErrorResponse("""
			{
				"title": "this is a title",
				"detail": "this is a detail"
			}
			""", httpStatus, null);

		// Act
		final var exception = errorDecoder.decode("test", errorResponse);

		// Assert
		assertThat(exception).isInstanceOf(type);
	}

	@Test
	void errorDecoderReturnsRetryableExceptionOnWSO2TokenExpire() {

		// Arrange
		final var errorDecoder = new ProblemErrorDecoder("XXX");
		final var errorResponse = buildErrorResponse("Error", 401, Map.of("www-authenticate", Set.of(WSO2_TOKEN_EXPIRE_HEADER_ERROR)));

		// Act
		final var exception = errorDecoder.decode("test", errorResponse);

		// Assert
		assertThat(exception).isInstanceOf(RetryableException.class);
	}

	@Test
	void errorDecoderReturnRetryableException() {

		// Arrange
		final var retryResponseVerifierMock = Mockito.mock(RetryResponseVerifier.class);
		final var errorDecoder = new ProblemErrorDecoder("XXX", emptyList(), retryResponseVerifierMock);
		final var errorResponse = buildErrorResponse("Error", 500, null);

		when(retryResponseVerifierMock.shouldReturnRetryableException(any())).thenReturn(true);
		when(retryResponseVerifierMock.getMessage()).thenReturn("Special message");

		// Act
		final var exception = errorDecoder.decode("test", errorResponse);

		// Assert
		verify(retryResponseVerifierMock).shouldReturnRetryableException(same(errorResponse));
		verify(retryResponseVerifierMock).getMessage();
		assertThat(exception).isExactlyInstanceOf(RetryableException.class);
		assertThat(exception.getMessage()).isEqualTo("Special message");
		assertThat(exception.getCause()).isInstanceOf(ServerProblem.class);
	}
}
