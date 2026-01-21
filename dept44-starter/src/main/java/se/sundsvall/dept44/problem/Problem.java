package se.sundsvall.dept44.problem;

import java.net.URI;

/**
 * Represents an RFC 7807 Problem Details object.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807</a>
 */
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
		return new DefaultProblem.Builder();
	}

	/**
	 * Create a ThrowableProblem with the given status and detail.
	 *
	 * @param  status the HTTP status
	 * @param  detail the detail message
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem valueOf(final StatusType status, final String detail) {
		return builder()
			.withStatus(status)
			.withTitle(status.getReasonPhrase())
			.withDetail(detail)
			.build();
	}

	/**
	 * Create a ThrowableProblem with the given status.
	 *
	 * @param  status the HTTP status
	 * @return        a new ThrowableProblem
	 */
	static ThrowableProblem valueOf(final StatusType status) {
		return builder()
			.withStatus(status)
			.withTitle(status.getReasonPhrase())
			.build();
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
	StatusType getStatus();

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
		Builder withStatus(StatusType status);

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
