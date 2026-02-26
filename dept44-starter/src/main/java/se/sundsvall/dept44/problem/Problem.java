package se.sundsvall.dept44.problem;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.annotation.JsonDeserialize;

import static java.text.MessageFormat.format;

/**
 * Represents an RFC 9457 Problem Details object.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9457">RFC 9457</a>
 */
@JsonDeserialize(as = ProblemResponse.class)
public interface Problem {

	/**
	 * The default problem type URI.
	 */
	URI DEFAULT_TYPE = URI.create("about:blank");

	/**
	 * Create a new Problem builder.
	 *
	 * @return a new builder instance
	 */
	static Builder builder() {
		return new ThrowableProblem.Builder();
	}

	/**
	 * Create a ThrowableProblem with the given status and detail.
	 *
	 * @param  status the HTTP status
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem valueOf(final HttpStatus status, final String detail) {
		return builder().withStatus(status).withTitle(status.getReasonPhrase()).withDetail(detail).build();
	}

	/**
	 * Create a ThrowableProblem with the given status.
	 *
	 * @param  status the HTTP status
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem valueOf(final HttpStatus status) {
		return builder().withStatus(status).withTitle(status.getReasonPhrase()).build();
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_REQUEST, corresponding title and no detail message.
	 *
	 * @return a new ThrowableProblem
	 */
	static ThrowableProblem badRequest() {
		return valueOf(HttpStatus.BAD_REQUEST);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_REQUEST, corresponding title and the given detail
	 * message.
	 *
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem badRequest(final String detail) {
		return valueOf(HttpStatus.BAD_REQUEST, detail);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_REQUEST, corresponding title and a detail message
	 * formatted from the given pattern and parameters.
	 *
	 * @param  detailPattern the detail message pattern
	 * @param  parameters    the detail message parameters
	 * @return               a new ThrowableProblem
	 */
	static ThrowableProblem badRequest(final String detailPattern, final Object... parameters) {
		return valueOf(HttpStatus.BAD_REQUEST, format(detailPattern, parameters));
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status NOT_FOUND, corresponding title and no detail message.
	 *
	 * @return a new ThrowableProblem
	 */
	static ThrowableProblem notFound() {
		return valueOf(HttpStatus.NOT_FOUND);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status NOT_FOUND, corresponding title and the given detail message.
	 *
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem notFound(final String detail) {
		return valueOf(HttpStatus.NOT_FOUND, detail);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status NOT_FOUND, corresponding title and a detail message
	 * formatted from the given pattern and parameters.
	 *
	 * @param  detailPattern the detail message pattern
	 * @param  parameters    the detail message parameters
	 * @return               a new ThrowableProblem
	 */
	static ThrowableProblem notFound(final String detailPattern, final Object... parameters) {
		return valueOf(HttpStatus.NOT_FOUND, format(detailPattern, parameters));
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status INTERNAL_SERVER_ERROR, corresponding title and no detail.
	 *
	 * @return a new ThrowableProblem
	 */
	static ThrowableProblem internalServerError() {
		return valueOf(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status INTERNAL_SERVER_ERROR, corresponding title and the given
	 * detail message.
	 *
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem internalServerError(final String detail) {
		return valueOf(HttpStatus.INTERNAL_SERVER_ERROR, detail);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status INTERNAL_SERVER_ERROR, corresponding title and a detail
	 * message formatted from the given pattern and parameters.
	 *
	 * @param  detailPattern the detail message pattern
	 * @param  parameters    the detail message parameters
	 * @return               a new ThrowableProblem
	 */
	static ThrowableProblem internalServerError(final String detailPattern, final Object... parameters) {
		return valueOf(HttpStatus.INTERNAL_SERVER_ERROR, format(detailPattern, parameters));
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_GATEWAY, corresponding title and no detail.
	 *
	 * @return a new ThrowableProblem
	 */
	static ThrowableProblem badGateway() {
		return valueOf(HttpStatus.BAD_GATEWAY);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_GATEWAY, corresponding title and the given detail
	 * message.
	 *
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem badGateway(final String detail) {
		return valueOf(HttpStatus.BAD_GATEWAY, detail);
	}

	/**
	 * Shortcut method to create a ThrowableProblem with status BAD_GATEWAY, corresponding title and a detail message
	 * formatted from the given pattern and parameters.
	 *
	 * @param  detailPattern the detail message pattern
	 * @param  parameters    the detail message parameters
	 * @return               a new ThrowableProblem
	 */
	static ThrowableProblem badGateway(final String detailPattern, final Object... parameters) {
		return valueOf(HttpStatus.BAD_GATEWAY, format(detailPattern, parameters));
	}

	/**
	 * Get the problem type URI.
	 *
	 * @return the type URI, or {@link #DEFAULT_TYPE} if not set
	 */
	URI getType();

	/**
	 * Get the problem title.
	 *
	 * @return the title
	 */
	String getTitle();

	/**
	 * Get the HTTP status.
	 *
	 * @return the status
	 */
	@JsonIgnore
	HttpStatus getStatus();

	/**
	 * Get the status as an integer for JSON serialization.
	 *
	 * @return the status code, or null if status is null
	 */
	@JsonGetter("status")
	default Integer getStatusValue() {
		final var s = getStatus();
		return s != null ? s.value() : null;
	}

	/**
	 * Get the problem detail message.
	 *
	 * @return the detail
	 */
	String getDetail();

	/**
	 * Get the problem instance URI.
	 *
	 * @return the instance URI
	 */
	URI getInstance();

	/**
	 * Builder interface for creating Problem instances.
	 */
	interface Builder {

		/**
		 * Set the problem type URI.
		 *
		 * @param  type the type URI
		 * @return      this builder
		 */
		Builder withType(URI type);

		/**
		 * Set the problem title.
		 *
		 * @param  title the title
		 * @return       this builder
		 */
		Builder withTitle(String title);

		/**
		 * Set the HTTP status.
		 *
		 * @param  status the status
		 * @return        this builder
		 */
		Builder withStatus(HttpStatus status);

		/**
		 * Set the problem detail message.
		 *
		 * @param  detail the detail
		 * @return        this builder
		 */
		Builder withDetail(String detail);

		/**
		 * Set the problem instance URI.
		 *
		 * @param  instance the instance URI
		 * @return          this builder
		 */
		Builder withInstance(URI instance);

		/**
		 * Set the cause of this problem.
		 *
		 * @param  cause the cause
		 * @return       this builder
		 */
		Builder withCause(ThrowableProblem cause);

		/**
		 * Build the Problem as a ThrowableProblem.
		 *
		 * @return the built ThrowableProblem
		 */
		ThrowableProblem build();
	}
}
