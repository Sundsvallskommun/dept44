package se.sundsvall.dept44.problem.violations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import se.sundsvall.dept44.problem.ThrowableProblem;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A Problem that represents constraint violations, typically from validation errors.
 */
@JsonDeserialize // Override the Problem interface's @JsonDeserialize(as = ThrowableProblem.class) annotation
public class ConstraintViolationProblem extends ThrowableProblem {

	/**
	 * The default type URI for constraint violation problems.
	 */
	public static final URI TYPE = URI.create("about:blank");

	/**
	 * The default title for constraint violation problems.
	 */
	public static final String DEFAULT_TITLE = "Constraint Violation";

	private final List<Violation> violations;

	/**
	 * Create a new ConstraintViolationProblem from JSON.
	 *
	 * @param type       the problem type URI
	 * @param status     the HTTP status code as integer
	 * @param violations the list of violations
	 */
	@JsonCreator
	public ConstraintViolationProblem(
		@JsonProperty("type") final URI type,
		@JsonProperty("status") final Integer status,
		@JsonProperty("violations") final List<Violation> violations) {
		this(type, status != null ? HttpStatus.valueOf(status) : null, violations, null);
	}

	/**
	 * Create a new ConstraintViolationProblem with a title.
	 *
	 * @param type       the problem type URI
	 * @param status     the HTTP status
	 * @param violations the list of violations
	 * @param title      the problem title
	 */
	public ConstraintViolationProblem(final URI type, final HttpStatus status, final List<Violation> violations, final String title) {
		super(
			type != null ? type : TYPE,
			title != null ? title : DEFAULT_TITLE,
			status,
			null,
			null,
			null);
		this.violations = violations != null ? List.copyOf(violations) : Collections.emptyList();
	}

	/**
	 * Create a new ConstraintViolationProblem with a default type.
	 *
	 * @param status     the HTTP status
	 * @param violations the list of violations
	 */
	public ConstraintViolationProblem(final HttpStatus status, final List<Violation> violations) {
		this(TYPE, status, violations, null);
	}

	/**
	 * Create a new builder for ConstraintViolationProblem.
	 *
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Get the list of violations.
	 *
	 * @return an unmodifiable list of violations
	 */
	public List<Violation> getViolations() {
		return violations;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final ConstraintViolationProblem that = (ConstraintViolationProblem) o;
		return Objects.equals(getType(), that.getType())
			&& Objects.equals(getStatus(), that.getStatus())
			&& Objects.equals(violations, that.violations);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getType(), getStatus(), violations);
	}

	/**
	 * Builder for ConstraintViolationProblem.
	 */
	public static class Builder {

		private URI type = TYPE;
		private HttpStatus status;
		private List<Violation> violations = Collections.emptyList();
		private String title;

		/**
		 * Set the problem type URI.
		 *
		 * @param  type the type URI
		 * @return      this builder
		 */
		public Builder withType(final URI type) {
			this.type = type;
			return this;
		}

		/**
		 * Set the HTTP status.
		 *
		 * @param  status the status
		 * @return        this builder
		 */
		public Builder withStatus(final HttpStatus status) {
			this.status = status;
			return this;
		}

		/**
		 * Set the violations.
		 *
		 * @param  violations the violations
		 * @return            this builder
		 */
		public Builder withViolations(final List<Violation> violations) {
			this.violations = violations;
			return this;
		}

		/**
		 * Set the problem title.
		 *
		 * @param  title the title
		 * @return       this builder
		 */
		public Builder withTitle(final String title) {
			this.title = title;
			return this;
		}

		/**
		 * Build the ConstraintViolationProblem.
		 *
		 * @return the built problem
		 */
		public ConstraintViolationProblem build() {
			return new ConstraintViolationProblem(type, status, violations, title);
		}
	}
}
