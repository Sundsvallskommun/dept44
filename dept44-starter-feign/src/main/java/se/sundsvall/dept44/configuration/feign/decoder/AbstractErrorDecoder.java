package se.sundsvall.dept44.configuration.feign.decoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.zalando.problem.Status.BAD_GATEWAY;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus.Series;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;

/**
 * The base error decoder.
 */
public abstract class AbstractErrorDecoder implements ErrorDecoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractErrorDecoder.class);

	protected String integrationName;
	protected List<Integer> bypassResponseCodes;

	protected RetryResponseVerifier retryResponseVerifier;

	/**
	 * Creates a new ErrorDecoder with an integration name and bypass response codes.
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
	protected AbstractErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes, final RetryResponseVerifier retryResponseVerifier) {
		this.integrationName = requireNonNull(integrationName);
		this.bypassResponseCodes = requireNonNull(bypassResponseCodes);
		this.retryResponseVerifier = retryResponseVerifier;
	}

	/**
	 * Creates a new ErrorDecoder with an integration name and no bypass response codes.
	 *
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * If the {@link RetryResponseVerifier} returns true a {@link RetryableException} will be thrown.
	 *
	 * @param integrationName       name of integration to whom the error decoder is connected
	 * @param retryResponseVerifier if verifier returns true a {@link RetryableException} will be returned
	 */
	protected AbstractErrorDecoder(@Nonnull final String integrationName, final RetryResponseVerifier retryResponseVerifier) {
		this.integrationName = integrationName;
		this.retryResponseVerifier = retryResponseVerifier;
	}

	@Override
	public Exception decode(final String methodKey, final Response response) {
		if (retryResponseVerifier != null && retryResponseVerifier.shouldReturnRetryableException(response)) {
			return new RetryableException(
				response.status(),
				retryResponseVerifier.getMessage(),
				response.request().httpMethod(),
				mapToProblem(response),
				null,
				response.request());
		}
		return mapToProblem(response);
	}

	private Exception mapToProblem(final Response response) {
		// Use the bypass status code if it matches the response code, otherwise BAD_GATEWAY.
		final var status = Optional.ofNullable(bypassResponseCodes).orElse(emptyList()).stream()
			.filter(bypassCode -> bypassCode.equals(response.status()))
			.map(Status::valueOf)
			.findAny()
			.orElse(BAD_GATEWAY);

		return switch (Series.valueOf(response.status())) {
			case CLIENT_ERROR -> new ClientProblem(status, extractMessage(response));
			case SERVER_ERROR -> new ServerProblem(status, extractMessage(response));
			default -> Problem.valueOf(status, extractMessage(response));
		};
	}

	protected String bodyAsString(final Response response) throws IOException {
		return new String(response.body().asInputStream().readAllBytes(), UTF_8);
	}

	private String extractMessage(final Response response) {
		try {
			// Body will be null for HTTP 401, 404, 407, etc. This is how the default decoder behaves in Feign.
			// Some services can also return empty string as body, which should be treated the same way as null.
			if (isNull(response.body()) || isBlank(bodyAsString(response))) {
				return extractAsNullBodyResponse(response);
			}

			// Call the implementation (as implemented by the subclasses).
			return extractErrorMessage(response);
		} catch (final Exception e) {
			return extractAsLastResort(response, e);
		}
	}

	/**
	 * Implement this method in order to create an String that represents the error.
	 *
	 * @param response the response that caused the error.
	 * @return a String that represents the error message returned in the response.
	 * @throws Exception if something goes wrong.
	 */
	public abstract String extractErrorMessage(Response response) throws Exception;

	private String extractAsNullBodyResponse(final Response response) {
		return ErrorMessage.create(integrationName, response.status()).extractMessage();
	}

	private String extractAsLastResort(final Response response, final Exception e) {
		LOGGER.warn("Something went wrong when extracting error-message", e);
		return ErrorMessage.create(integrationName, response.status(), "Unknown error", null).extractMessage();
	}

	/**
	 * Private record to used for calculating extracted error message information
	 */
	protected static record ErrorMessage(String integrationName, SortedMap<String, Object> errorInfo) {

		private static final String KEY_DETAIL = "detail";
		private static final String KEY_STATUS = "status";
		private static final String KEY_TITLE = "title";
		private static final String ERROR_TEMPLATE = "%s error: %s";

		static ErrorMessage create(final String integrationName, final int httpStatus, final Problem problem) {
			return create(integrationName, httpStatus, problem.getTitle(), problem.getDetail());
		}

		static ErrorMessage create(final String integrationName, final int httpStatus) {
			return create(integrationName, httpStatus, Map.of(KEY_TITLE, Status.valueOf(httpStatus).getReasonPhrase()));
		}

		static ErrorMessage create(final String integrationName, final int httpStatus, final String title, final String detail) {
			final SortedMap<String, Object> map = new TreeMap<>();
			ofNullable(title).ifPresent(value -> map.put(KEY_TITLE, value));
			ofNullable(detail).ifPresent(value -> map.put(KEY_DETAIL, value));
			return create(integrationName, httpStatus, map);
		}

		private static ErrorMessage create(final String integrationName, final int httpStatus, final Map<String, Object> errorInfo) {
			final SortedMap<String, Object> map = new TreeMap<>();
			map.put(KEY_STATUS, Status.valueOf(httpStatus));
			if (nonNull(errorInfo)) {
				map.putAll(errorInfo);
			}

			return new ErrorMessage(integrationName, map);
		}

		/**
		 * Method to get error message. Message is calculated based on available data in the error message record.
		 *
		 * @return a string containing the calculated error message
		 */
		String extractMessage() {
			return String.format(ERROR_TEMPLATE, integrationName, errorInfo);
		}
	}
}
