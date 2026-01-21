package se.sundsvall.dept44.problem;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;

/**
 * HTTP status codes as defined in RFC 7231 and RFC 6585. Delegates to Spring's HttpStatus internally.
 */
public enum Status implements StatusType {

	// 1xx Informational
	CONTINUE(HttpStatus.CONTINUE),
	SWITCHING_PROTOCOLS(HttpStatus.SWITCHING_PROTOCOLS),

	// 2xx Success
	OK(HttpStatus.OK),
	CREATED(HttpStatus.CREATED),
	ACCEPTED(HttpStatus.ACCEPTED),
	NON_AUTHORITATIVE_INFORMATION(HttpStatus.NON_AUTHORITATIVE_INFORMATION),
	NO_CONTENT(HttpStatus.NO_CONTENT),
	RESET_CONTENT(HttpStatus.RESET_CONTENT),
	PARTIAL_CONTENT(HttpStatus.PARTIAL_CONTENT),
	MULTI_STATUS(HttpStatus.MULTI_STATUS),
	ALREADY_REPORTED(HttpStatus.ALREADY_REPORTED),
	IM_USED(HttpStatus.IM_USED),

	// 3xx Redirection
	MULTIPLE_CHOICES(HttpStatus.MULTIPLE_CHOICES),
	MOVED_PERMANENTLY(HttpStatus.MOVED_PERMANENTLY),
	FOUND(HttpStatus.FOUND),
	SEE_OTHER(HttpStatus.SEE_OTHER),
	NOT_MODIFIED(HttpStatus.NOT_MODIFIED),
	TEMPORARY_REDIRECT(HttpStatus.TEMPORARY_REDIRECT),
	PERMANENT_REDIRECT(HttpStatus.PERMANENT_REDIRECT),

	// 4xx Client Error
	BAD_REQUEST(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
	PROXY_AUTHENTICATION_REQUIRED(HttpStatus.PROXY_AUTHENTICATION_REQUIRED),
	REQUEST_TIMEOUT(HttpStatus.REQUEST_TIMEOUT),
	CONFLICT(HttpStatus.CONFLICT),
	GONE(HttpStatus.GONE),
	LENGTH_REQUIRED(HttpStatus.LENGTH_REQUIRED),
	PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED),
	URI_TOO_LONG(HttpStatus.URI_TOO_LONG),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	REQUESTED_RANGE_NOT_SATISFIABLE(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE),
	EXPECTATION_FAILED(HttpStatus.EXPECTATION_FAILED),
	LOCKED(HttpStatus.LOCKED),
	FAILED_DEPENDENCY(HttpStatus.FAILED_DEPENDENCY),
	TOO_EARLY(HttpStatus.TOO_EARLY),
	UPGRADE_REQUIRED(HttpStatus.UPGRADE_REQUIRED),
	PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
	REQUEST_HEADER_FIELDS_TOO_LARGE(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE),
	UNAVAILABLE_FOR_LEGAL_REASONS(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS),

	// 5xx Server Error
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED),
	BAD_GATEWAY(HttpStatus.BAD_GATEWAY),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
	HTTP_VERSION_NOT_SUPPORTED(HttpStatus.HTTP_VERSION_NOT_SUPPORTED),
	VARIANT_ALSO_NEGOTIATES(HttpStatus.VARIANT_ALSO_NEGOTIATES),
	INSUFFICIENT_STORAGE(HttpStatus.INSUFFICIENT_STORAGE),
	LOOP_DETECTED(HttpStatus.LOOP_DETECTED),
	NETWORK_AUTHENTICATION_REQUIRED(HttpStatus.NETWORK_AUTHENTICATION_REQUIRED);

	private final HttpStatus httpStatus;

	Status(final HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	/**
	 * Get a Status from an HTTP status code.
	 *
	 * @param  statusCode               the HTTP status code
	 * @return                          the corresponding Status
	 * @throws IllegalArgumentException if no Status matches the code
	 */
	public static Status valueOf(final int statusCode) {
		for (final Status status : values()) {
			if (status.getStatusCode() == statusCode) {
				return status;
			}
		}
		throw new IllegalArgumentException("No Status found for status code: " + statusCode);
	}

	/**
	 * Get a Status from a Spring HttpStatus.
	 *
	 * @param  httpStatus the Spring HttpStatus
	 * @return            the corresponding Status
	 */
	public static Status fromHttpStatus(final HttpStatus httpStatus) {
		return valueOf(httpStatus.value());
	}

	@JsonValue
	@Override
	public int getStatusCode() {
		return httpStatus.value();
	}

	@Override
	public String getReasonPhrase() {
		return httpStatus.getReasonPhrase();
	}

	/**
	 * Get the underlying Spring HttpStatus.
	 *
	 * @return the HttpStatus
	 */
	public HttpStatus toHttpStatus() {
		return httpStatus;
	}

	@Override
	public String toString() {
		return getStatusCode() + " " + getReasonPhrase();
	}
}
