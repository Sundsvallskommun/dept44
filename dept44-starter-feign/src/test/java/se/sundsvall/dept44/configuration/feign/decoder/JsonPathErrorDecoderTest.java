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
import se.sundsvall.dept44.configuration.feign.decoder.JsonPathErrorDecoder.JsonPathSetup;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;
import se.sundsvall.dept44.test.annotation.resource.Load;
import se.sundsvall.dept44.test.extension.ResourceLoaderExtension;

@ExtendWith(ResourceLoaderExtension.class)
class JsonPathErrorDecoderTest {

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
		assertThat(ErrorDecoder.class).isAssignableFrom(JsonPathErrorDecoder.class);
		assertThat(AbstractErrorDecoder.class).isAssignableFrom(JsonPathErrorDecoder.class);
	}

	@Test
	void decodeCustomError1(@Load("customError1.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['Message']", "$['Detail']"));
		final var response = buildErrorResponse(errorBody, 409, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Bad Gateway: XXX error: {detail=A minor detail, status=409 Conflict, title=This is a custom error}");
	}

	@Test
	void decodeCustomError1WithNoDetailPath(@Load("customError1.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['Message']"));
		final var response = buildErrorResponse(errorBody, 409, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Bad Gateway: XXX error: {status=409 Conflict, title=This is a custom error}");
	}

	@Test
	void decodeCustomError2(@Load("customError2.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['errorMessage']", "concat($['extraInfo']['details'], \" with custom status \", $['extraInfo']['status'])"));
		final var response = buildErrorResponse(errorBody, 500, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Bad Gateway: XXX error: {detail=This is details with custom status 409 Conflict, status=500 Internal Server Error, title=This is a custom error}");
	}

	@Test
	void decodeCustomError3(@Load("customError3.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX",
			new JsonPathSetup("concat($.errorMessage, \" - \" ,$.extraInfo.errorDetail)", "$.extraInfo.details"));
		final var response = buildErrorResponse(errorBody, 409, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Bad Gateway: XXX error: {detail=This is details, status=409 Conflict, title=This is a custom error - with extrainfo}");
	}

	@Test
	void decodeCustomError3WithNoTitlePath(@Load("customError3.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup(null, "$.extraInfo.details"));
		final var response = buildErrorResponse(errorBody, 409, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Bad Gateway: XXX error: {detail=This is details, status=409 Conflict}");
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderForErrorMessages")
	void errorDecoderForErrorMessages(final String body, final int httpStatus, final String expectedMessage) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['title']", "$['detail']"));
		final var response = buildErrorResponse(body, httpStatus, null);

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage(expectedMessage);
	}

	@Test
	void errorDecoderWhenBypassResponseCodesAreSet() {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", List.of(400, 401, 404, 409), new JsonPathSetup("$['title']", "$['detail']"));
		final var response = buildErrorResponse(null, 404, null); // statusCode exists in bypassList.

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Not Found: XXX error: {status=404 Not Found, title=Not Found}");
	}

	@Test
	void errorDecoderWhenBypassResponseCodesAndJsonPathSetupAreSet(@Load("customError1.json") final String errorBody) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", List.of(400, 401, 404, 409), new JsonPathSetup("$['Message']", "$['Detail']"));
		final var response = buildErrorResponse(errorBody, 404, null); // statusCode exists in bypassList.

		// Execute
		final var exception = errorDecoder.decode("test", response);

		// Verify
		assertThat(exception).hasMessage("Not Found: XXX error: {detail=A minor detail, status=404 Not Found, title=This is a custom error}");
	}

	@ParameterizedTest
	@MethodSource("toErrorDecoderReturnsCorrectThrowableType")
	void errorDecoderReturnsCorrectThrowableType(final int httpStatus, final Class<ThrowableProblem> type) {

		// Setup
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['title']", "$['detail']"));
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
		final var errorDecoder = new JsonPathErrorDecoder("XXX", new JsonPathSetup("$['title']", "$['detail']"));
		final var errorResponse = buildErrorResponse("Error", 401, Map.of("www-authenticate", Set.of(WSO2_TOKEN_EXPIRE_HEADER_ERROR)));

		final var exception = errorDecoder.decode("test", errorResponse);

		assertThat(exception).isInstanceOf(RetryableException.class);

	}

	@Test
	void errorDecoderReturnsRetryableException() {
		final var retryResponseVerifierMock = Mockito.mock(RetryResponseVerifier.class);
		final var errorDecoder = new JsonPathErrorDecoder("XXX", emptyList(), new JsonPathSetup("$['title']", "$['detail']"), retryResponseVerifierMock);
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

}
