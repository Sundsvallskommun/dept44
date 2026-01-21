package se.sundsvall.dept44.configuration.feign.decoder;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import feign.Response;
import feign.RetryableException;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.Status;
import se.sundsvall.dept44.problem.StatusType;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Problem ErrorDecoder that allows you to process a Problem-based error response.
 * <p>
 * This decoder manages error responses based on: <a href="https://datatracker.ietf.org/doc/html/rfc9457">RFC 9457</a>
 */
public class ProblemErrorDecoder extends AbstractErrorDecoder {

	private static final JsonMapper JSON_MAPPER_MAPPER = JsonMapper.builder()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
		.build();

	/**
	 * Creates a new ProblemErrorDecoder with an integration name and bypass response codes.
	 * <p>
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 * <p>
	 * The bypass response codes will be propagated as the original response code, instead of being wrapped in a
	 * ThrowableProblem with a BAD_GATEWAY-code. I.e., if '404' is provided in the bypassResponseCode-list and the actual
	 * response code is matching
	 * this value, a ThrowableProblem with NotFound-code will be returned from the decode-method.
	 *
	 * @param integrationName     name of integration to whom the error decoder is connected
	 * @param bypassResponseCodes list of response codes to bypass
	 */
	public ProblemErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes) {
		super(integrationName, bypassResponseCodes, new WSO2RetryResponseVerifier());
	}

	/**
	 * Creates a new ProblemErrorDecoder with an integration name and no bypass response codes.
	 * <p>
	 * The integration name will be used in all Exceptions returned by the decode-method.
	 *
	 * @param integrationName name of integration to whom the error decoder is connected
	 */
	public ProblemErrorDecoder(@Nonnull final String integrationName) {
		super(integrationName, new WSO2RetryResponseVerifier());
	}

	/**
	 * /** Creates a new ProblemErrorDecoder with an integration name and bypass response codes.
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
	public ProblemErrorDecoder(@Nonnull final String integrationName, @Nonnull final List<Integer> bypassResponseCodes, final RetryResponseVerifier retryResponseVerifier) {
		super(integrationName, bypassResponseCodes, retryResponseVerifier);
	}

	@Override
	public String extractErrorMessage(final Response response) throws IOException {
		// Read the body once and reuse it for all parsings
		final var body = bodyAsString(response);
		final Problem problem = extractProblem(body);

		return ErrorMessage.create(integrationName, response.status(), problem).extractMessage();
	}

	private Problem extractProblem(final String body) {
		// First, try to deserialize as ConstraintViolationProblemResponse
		try {
			final var cvpResponse = JSON_MAPPER_MAPPER.readValue(body, ConstraintViolationProblemResponse.class);
			if (isNotEmpty(cvpResponse.violations())) {
				// Convert violations to detail string and create a Problem
				return toProblem(cvpResponse);
			}
		} catch (final Exception _) {
			// Not a ConstraintViolationProblem, fall through to try DefaultProblemResponse
		}

		// Fall back to DefaultProblemResponse - let the exception propagate if this also fails
		return JSON_MAPPER_MAPPER.readValue(body, DefaultProblemResponse.class);
	}

	private Problem toProblem(final ConstraintViolationProblemResponse cvpResponse) {
		final var violationsString = cvpResponse.violations().stream()
			.map(v -> "%s: %s".formatted(v.field(), v.message()))
			.collect(Collectors.joining(", "));

		return Problem.builder()
			.withStatus(cvpResponse.status() != null ? Status.valueOf(cvpResponse.status()) : null)
			.withTitle(cvpResponse.title() != null ? cvpResponse.title() : "Constraint Violation")
			.withDetail(violationsString)
			.build();
	}

	/**
	 * Simple DTO for deserializing ConstraintViolationProblem JSON responses. This avoids Jackson issues with the actual
	 * ConstraintViolationProblem class.
	 */
	record ConstraintViolationProblemResponse(String type, String title, Integer status, List<ViolationResponse> violations) {
	}

	/**
	 * Simple DTO for deserializing Violation JSON.
	 */
	record ViolationResponse(String field, String message) {
	}

	/**
	 * A simple POJO for deserializing Problem JSON responses. This is used instead of deserializing to the Problem
	 * interface directly.
	 */
	@JsonDeserialize // Override the Problem interface's @JsonDeserialize annotation
	static class DefaultProblemResponse implements Problem {

		private String type;
		private String title;
		private Integer status;
		private String detail;
		private String instance;

		@Override
		public URI getType() {
			return type != null ? URI.create(type) : Problem.DEFAULT_TYPE;
		}

		public void setType(final String type) {
			this.type = type;
		}

		@Override
		public String getTitle() {
			return title;
		}

		public void setTitle(final String title) {
			this.title = title;
		}

		@Override
		public StatusType getStatus() {
			return status != null ? Status.valueOf(status) : null;
		}

		public void setStatus(final Integer status) {
			this.status = status;
		}

		@Override
		public String getDetail() {
			return detail;
		}

		public void setDetail(final String detail) {
			this.detail = detail;
		}

		@Override
		public URI getInstance() {
			return instance != null ? URI.create(instance) : null;
		}

		public void setInstance(final String instance) {
			this.instance = instance;
		}
	}
}
