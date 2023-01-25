package se.sundsvall.dept44.configuration.feign.decoder;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import com.jayway.jsonpath.JsonPath;

import feign.Response;
import feign.RetryableException;

/**
 * A flexible ErrorDecoder that allows you to massage an error response into a application-specific one using JsonPath.
 *
 * This decoder can manage all types of JSON-based error responses, but should be used when no other specialized
 * ErrorDecoder exists.
 */
public class JsonPathErrorDecoder extends AbstractErrorDecoder {

	private final JsonPathSetup jsonPathSetup;

	/**
	 * Creates a new JsonPathErrorDecoder with an integration name and bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e. if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 *
	 * @param integrationName     name of integration to whom the error decoder is connected
	 * @param bypassResponseCodes list of response codes to bypass
	 * @param jsonPathSetup       the JSON paths for custom errorMessage parsing.
	 */
	public JsonPathErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes, @Nonnull final JsonPathSetup jsonPathSetup) {
		super(integrationName, bypassResponseCodes, new WSO2RetryResponseVerifier());

		this.jsonPathSetup = requireNonNull(jsonPathSetup);
	}

	/**
	 * Creates a new JsonPathErrorDecoder with an integration name and no bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * @param integrationName name of integration to whom the error decoder is connected
	 * @param jsonPathSetup   the JSON paths for custom errorMessage parsing.
	 */
	public JsonPathErrorDecoder(@Nonnull final String integrationName, @Nonnull final JsonPathSetup jsonPathSetup) {
		super(integrationName, new WSO2RetryResponseVerifier());

		this.jsonPathSetup = requireNonNull(jsonPathSetup);
	}

	/**
	 * Creates a new JsonPathErrorDecoder with an integration name and bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e. if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 *
	 * @param integrationName       name of integration to whom the error decoder is connected
	 * @param bypassResponseCodes   list of response codes to bypass
	 * @param jsonPathSetup         the JSON paths for custom errorMessage parsing.
	 * @param retryResponseVerifier if verifier returns true a {@link RetryableException} will be returned
	 */
	public JsonPathErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes, @Nonnull final JsonPathSetup jsonPathSetup, final RetryResponseVerifier retryResponseVerifier) {
		super(integrationName, bypassResponseCodes, retryResponseVerifier);

		this.jsonPathSetup = requireNonNull(jsonPathSetup);
	}

	@Override
	public String extractErrorMessage(final Response response) throws IOException {
		final var parsedJson = JsonPath.parse(bodyAsString(response));
		final var title = nonNull(this.jsonPathSetup.titlePath()) ? parsedJson.read(this.jsonPathSetup.titlePath(), String.class) : null;
		final var detail = nonNull(this.jsonPathSetup.detailPath()) ? parsedJson.read(this.jsonPathSetup.detailPath(), String.class) : null;
		return ErrorMessage.create(integrationName, response.status(), title, detail).extractMessage();
	}

	/**
	 * Creates a new JsonPathSetup with paths to the content of title and detail.
	 *
	 * @param titlePath  the JSON path to the title content in the error body.
	 * @param detailPath the JSON path to the detail content in the error body.
	 */
	public static record JsonPathSetup(String titlePath, String detailPath) {

		/**
		 * Creates a new JsonPathSetup with JSON path to the content of the title.
		 *
		 * @param titlePath the JSON path to the title content in the error body.
		 */
		public JsonPathSetup(final String titlePath) {
			this(titlePath, null);
		}
	}
}
