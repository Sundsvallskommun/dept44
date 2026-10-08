package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import se.sundsvall.dept44.exception.ClientProblem;
import se.sundsvall.dept44.exception.ServerProblem;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * The base error decoder.
 */
public abstract class AbstractErrorDecoder implements ErrorDecoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractErrorDecoder.class);

	/**
	 * The most of an error body that is read to find the error message. Payload logging keeps no more than this of an
	 * error body that is larger than it may capture.
	 */
	public static final int MAX_ERROR_BODY_SIZE = 1024 * 1024;

	protected final String integrationName;
	protected final RetryResponseVerifier retryResponseVerifier;
	protected List<Integer> bypassResponseCodes;

	/**
	 * Creates a new ErrorDecoder with an integration name and bypass response codes.
	 * <p>
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 * <p>
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e., if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching
	 * this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 * <p>
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
	 * <p>
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 * <p>
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
	public Exception decode(final String methodKey, final Response originalResponse) {
		final var response = withRepeatableBody(originalResponse);
		if ((retryResponseVerifier != null) && retryResponseVerifier.shouldReturnRetryableException(response)) {
			return new RetryableException(
				response.status(),
				retryResponseVerifier.getMessage(),
				response.request().httpMethod(),
				mapToProblem(response),
				(Long) null,
				response.request());
		}
		return mapToProblem(response);
	}

	private Exception mapToProblem(final Response response) {
		// Use the bypass status code if it matches the response code, otherwise BAD_GATEWAY.
		final var status = Optional.ofNullable(bypassResponseCodes).orElse(emptyList()).stream()
			.filter(bypassCode -> bypassCode.equals(response.status()))
			.map(HttpStatus::resolve)
			.filter(Objects::nonNull)
			.findAny()
			.orElse(BAD_GATEWAY);

		// A status outside 100-599 (some proxies and firewalls answer 999) has no series
		return switch (Series.resolve(response.status())) {
			case CLIENT_ERROR -> new ClientProblem(status, extractMessage(response));
			case SERVER_ERROR -> new ServerProblem(status, extractMessage(response));
			case null, default -> Problem.valueOf(status, extractMessage(response));
		};
	}

	/**
	 * The message is extracted by reading the body more than once (to check whether it is blank, then by the subclass).
	 * A body that can only be read once, such as a streamed body when Feign's logger level is NONE, is read into memory
	 * first, at most {@value #MAX_ERROR_BODY_SIZE} bytes of it.
	 */
	private static Response withRepeatableBody(final Response response) {
		if (isNull(response.body()) || response.body().isRepeatable()) {
			return response;
		}
		try (final var input = response.body().asInputStream()) {
			return response.toBuilder().body(input.readNBytes(MAX_ERROR_BODY_SIZE)).build();
		} catch (final IOException e) {
			LOGGER.warn("Could not read the error response body", e);
			return response;
		}
	}

	/**
	 * Reads at most the first {@value #MAX_ERROR_BODY_SIZE} bytes of the body: an error message never needs more, and a
	 * large error body is never read into memory as a whole.
	 */
	protected String bodyAsString(final Response response) throws IOException {
		try (final var input = response.body().asInputStream()) {
			return new String(input.readNBytes(MAX_ERROR_BODY_SIZE), UTF_8);
		}
	}

	private String extractMessage(final Response response) {
		try {
			// Body will be null for HTTP 401, 404, 407, etc. This is how the default decoder behaves in Feign.
			// Some services can also return an empty string as a body, which should be treated the same way as null.
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
	 * Implement this method to create a String that represents the error.
	 *
	 * @param  response    the response that caused the error.
	 * @return             a String that represents the error message returned in the response.
	 * @throws IOException if something goes wrong.
	 */
	public abstract String extractErrorMessage(Response response) throws IOException;

	private String extractAsNullBodyResponse(final Response response) {
		return ErrorMessage.create(integrationName, response.status()).extractMessage();
	}

	private String extractAsLastResort(final Response response, final Exception e) {
		LOGGER.warn("Something went wrong when extracting error-message", e);
		return ErrorMessage.create(integrationName, response.status(), "Unknown error", null).extractMessage();
	}

	/**
	 * Private record to use for calculating extracted error message information.
	 *
	 * @param integrationName the name of the integration (clientId)
	 * @param errorInfo       the set of error detail information
	 */
	protected record ErrorMessage(String integrationName, SortedMap<String, Object> errorInfo) {

		private static final String KEY_DETAIL = "detail";
		private static final String KEY_STATUS = "status";
		private static final String KEY_TITLE = "title";
		private static final String ERROR_TEMPLATE = "%s error: %s";

		static ErrorMessage create(final String integrationName, final int httpStatus, final Problem problem) {
			return create(integrationName, httpStatus, problem.getTitle(), problem.getDetail());
		}

		static ErrorMessage create(final String integrationName, final int httpStatus) {
			return create(integrationName, httpStatus, Map.of(KEY_TITLE, reasonPhrase(httpStatus)));
		}

		static ErrorMessage create(final String integrationName, final int httpStatus, final String title, final String detail) {
			final SortedMap<String, Object> map = new TreeMap<>();
			ofNullable(title).ifPresent(value -> map.put(KEY_TITLE, value));
			ofNullable(detail).ifPresent(value -> map.put(KEY_DETAIL, value));
			return create(integrationName, httpStatus, map);
		}

		private static ErrorMessage create(final String integrationName, final int httpStatus, final Map<String, Object> errorInfo) {
			final SortedMap<String, Object> map = new TreeMap<>();
			map.put(KEY_STATUS, httpStatus + " " + reasonPhrase(httpStatus));

			ofNullable(errorInfo).ifPresent(map::putAll);

			return new ErrorMessage(integrationName, map);
		}

		/**
		 * Status codes such as 499 or 520 are used by proxies and CDNs but have no {@link HttpStatus} constant.
		 */
		private static String reasonPhrase(final int httpStatus) {
			return Optional.ofNullable(HttpStatus.resolve(httpStatus))
				.map(HttpStatus::getReasonPhrase)
				.orElse("Unknown Status");
		}

		/**
		 * Method to get an error message. Message is calculated based on available data in the error message record.
		 *
		 * @return a string containing the calculated error message
		 */
		String extractMessage() {
			return ERROR_TEMPLATE.formatted(integrationName, errorInfo);
		}
	}
}
