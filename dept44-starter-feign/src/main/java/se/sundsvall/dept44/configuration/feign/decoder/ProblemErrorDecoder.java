package se.sundsvall.dept44.configuration.feign.decoder;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import org.zalando.problem.Problem;
import org.zalando.problem.jackson.ProblemModule;

import com.fasterxml.jackson.databind.json.JsonMapper;

import feign.Response;
import feign.RetryableException;

/**
 * A Problem ErrorDecoder that allows you to process an Problem-based error response.
 *
 * This decoder manage error responses based on: https://datatracker.ietf.org/doc/html/rfc7807
 */
public class ProblemErrorDecoder extends AbstractErrorDecoder {

	private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().addModule(new ProblemModule()).build();

	/**
	 * Creates a new ProblemErrorDecoder with an integration name and bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e. if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 *
	 * @param integrationName     name of integration to whom the error decoder is connected
	 * @param bypassResponseCodes list of response codes to bypass
	 */
	public ProblemErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes) {
		super(integrationName, bypassResponseCodes, new WSO2RetryResponseVerifier());
	}

	/**
	 * Creates a new ProblemErrorDecoder with an integration name and no bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * @param integrationName name of integration to whom the error decoder is connected
	 */
	public ProblemErrorDecoder(@Nonnull final String integrationName) {
		super(integrationName, new WSO2RetryResponseVerifier());
	}

	/**
	 * /** Creates a new ProblemErrorDecoder with an integration name and bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e. if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 *
	 * If the {@link RetryResponseVerifier} returns true a {@link RetryableException} will be thrown.
	 *
	 * @param integrationName       name of integration to whom the error decoder is connected
	 * @param bypassResponseCodes   list of response codes to bypass
	 * @param retryResponseVerifier if verifier returns true a {@link RetryableException} will be returned
	 */
	public ProblemErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes, final RetryResponseVerifier retryResponseVerifier) {
		super(integrationName, bypassResponseCodes, retryResponseVerifier);
	}

	@Override
	public String extractErrorMessage(final Response response) throws IOException {
		final var problem = OBJECT_MAPPER.readValue(bodyAsString(response), Problem.class);
		return ErrorMessage.create(integrationName, response.status(), problem).extractMessage();
	}
}
