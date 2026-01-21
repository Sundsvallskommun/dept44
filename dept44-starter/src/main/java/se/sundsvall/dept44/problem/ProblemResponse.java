package se.sundsvall.dept44.problem;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblemResponse;

/**
 * A simple POJO implementation of Problem for JSON serialization. This class is used for response bodies where we need
 * a Problem-formatted response without the serialization issues that come with ThrowableProblem (which extends
 * Exception and has cyclic
 * references).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemResponse implements Problem {

	private URI type;
	private String title;
	private StatusType status;
	private String detail;
	private URI instance;

	/**
	 * Create an empty ProblemResponse.
	 */
	public ProblemResponse() {
		// type defaults to null, which is omitted in JSON serialization
	}

	/**
	 * Create a ProblemResponse from a Problem.
	 *
	 * @param problem the problem to copy from
	 */
	public ProblemResponse(final Problem problem) {
		// Only include the type if it's not the default value
		final var problemType = problem.getType();
		this.type = (problemType != null && !Problem.DEFAULT_TYPE.equals(problemType)) ? problemType : null;
		this.title = problem.getTitle();
		this.status = problem.getStatus();
		this.detail = problem.getDetail();
		this.instance = problem.getInstance();
	}

	/**
	 * Create a ProblemResponse from a ThrowableProblem. If the problem is a ConstraintViolationProblem, a
	 * ConstraintViolationProblemResponse is returned to preserve the violations.
	 *
	 * @param  problem the throwable problem
	 * @return         a new ProblemResponse (or ConstraintViolationProblemResponse for constraint violations)
	 */
	public static ProblemResponse from(final ThrowableProblem problem) {
		if (problem instanceof final ConstraintViolationProblem constraintViolationProblem) {
			return new ConstraintViolationProblemResponse(constraintViolationProblem);
		}
		return new ProblemResponse(problem);
	}

	@Override
	@JsonIgnore
	public URI getType() {
		return type != null ? type : Problem.DEFAULT_TYPE;
	}

	public void setType(final URI type) {
		this.type = type;
	}

	/**
	 * Get the type for JSON serialization (null if default to omit from output).
	 *
	 * @return the type URI or null if it's the default type
	 */
	@JsonGetter("type")
	public URI getTypeForJson() {
		return type;
	}

	@Override
	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	@Override
	@JsonIgnore
	public StatusType getStatus() {
		return status;
	}

	public void setStatus(final StatusType status) {
		this.status = status;
	}

	/**
	 * Get the status code as an integer for JSON serialization.
	 *
	 * @return the status code, or null if status is null
	 */
	@JsonGetter("status")
	public Integer getStatusCode() {
		return status != null ? status.getStatusCode() : null;
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
		return instance;
	}

	public void setInstance(final URI instance) {
		this.instance = instance;
	}
}
