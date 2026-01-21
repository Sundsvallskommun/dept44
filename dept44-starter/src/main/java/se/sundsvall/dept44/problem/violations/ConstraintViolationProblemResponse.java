package se.sundsvall.dept44.problem.violations;

import static java.util.Optional.ofNullable;

import java.util.Collections;
import java.util.List;
import se.sundsvall.dept44.problem.ProblemResponse;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A ProblemResponse that includes constraint violations. This class is used for JSON serialization of
 * ConstraintViolationProblem without the serialization issues that come with ThrowableProblem.
 */
@JsonDeserialize // Override inherited @JsonDeserialize annotation
public class ConstraintViolationProblemResponse extends ProblemResponse {

	private final List<Violation> violations;

	/**
	 * Create a ConstraintViolationProblemResponse from a ConstraintViolationProblem.
	 *
	 * @param problem the constraint violation problem to copy from
	 */
	public ConstraintViolationProblemResponse(final ConstraintViolationProblem problem) {
		super(problem);
		this.violations = ofNullable(problem.getViolations())
			.map(List::copyOf)
			.orElseGet(Collections::emptyList);
	}

	/**
	 * Get the list of violations.
	 *
	 * @return an unmodifiable list of violations
	 */
	public List<Violation> getViolations() {
		return violations;
	}
}
