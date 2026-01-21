package se.sundsvall.dept44.problem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An exception that represents an RFC 9457 Problem Details object. Extends Spring's ErrorResponseException to integrate
 * with Spring's native error handling.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9457">RFC 9457</a>
 */
@JsonIgnoreProperties({
	"body", "headers", "detailMessageCode", "detailMessageArguments", "titleMessageCode",
	"typeMessageCode", "cause", "stackTrace", "localizedMessage", "message", "suppressed",
	"mostSpecificCause", "rootCause", "statusCode"
})
public class ThrowableProblem extends ErrorResponseException implements Problem {

	private final URI type;
	private final String title;
	private final StatusType status;
	private final String detail;
	private final URI instance;

	/**
	 * Create a new ThrowableProblem.
	 *
	 * @param type     the problem type URI
	 * @param title    the problem title
	 * @param status   the HTTP status
	 * @param detail   the problem detail
	 * @param instance the problem instance URI
	 * @param cause    the cause of this problem
	 */
	public ThrowableProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance, final ThrowableProblem cause) {
		super(status != null ? HttpStatus.valueOf(status.getStatusCode()) : HttpStatus.INTERNAL_SERVER_ERROR, createProblemDetail(type, title, status, detail, instance), cause);
		this.type = type != null ? type : DEFAULT_TYPE;
		this.title = title;
		this.status = status;
		this.detail = detail;
		this.instance = instance;
	}

	/**
	 * Create a new ThrowableProblem without a cause.
	 *
	 * @param type     the problem type URI
	 * @param title    the problem title
	 * @param status   the HTTP status
	 * @param detail   the problem detail
	 * @param instance the problem instance URI
	 */
	public ThrowableProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance) {
		this(type, title, status, detail, instance, null);
	}

	/**
	 * Create a new ThrowableProblem from JSON.
	 *
	 * @param type     the problem type URI
	 * @param title    the problem title
	 * @param status   the HTTP status code as integer
	 * @param detail   the problem detail
	 * @param instance the problem instance URI
	 */
	@JsonCreator
	public ThrowableProblem(
		@JsonProperty("type") final URI type,
		@JsonProperty("title") final String title,
		@JsonProperty("status") final Integer status,
		@JsonProperty("detail") final String detail,
		@JsonProperty("instance") final URI instance) {
		this(type, title, status != null ? Status.valueOf(status) : null, detail, instance, null);
	}

	private static ProblemDetail createProblemDetail(final URI type, final String title, final StatusType status, final String detail, final URI instance) {
		final var problemDetail = ProblemDetail.forStatus(status != null ? status.getStatusCode() : 500);
		if (type != null) {
			problemDetail.setType(type);
		}
		if (title != null) {
			problemDetail.setTitle(title);
		}
		if (detail != null) {
			problemDetail.setDetail(detail);
		}
		if (instance != null) {
			problemDetail.setInstance(instance);
		}
		return problemDetail;
	}

	@Override
	public URI getType() {
		return type;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public StatusType getStatus() {
		return status;
	}

	@Override
	public String getDetail() {
		return detail;
	}

	@Override
	public URI getInstance() {
		return instance;
	}

	@Override
	public String getMessage() {
		if (title != null && detail != null) {
			return title + ": " + detail;
		}
		if (detail != null) {
			return detail;
		}
		if (title != null) {
			return title;
		}
		return status != null ? status.getReasonPhrase() : "Unknown problem";
	}

	/**
	 * Get the cause as a ThrowableProblem if it is one.
	 *
	 * @return the cause as ThrowableProblem, or null
	 */
	public ThrowableProblem getCauseAsProblem() {
		final var cause = getCause();
		return cause instanceof final ThrowableProblem throwableProblem ? throwableProblem : null;
	}
}
