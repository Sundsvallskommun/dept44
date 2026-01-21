package se.sundsvall.dept44.exception;

import se.sundsvall.dept44.problem.AbstractThrowableProblem;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.StatusType;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A ThrowableProblem to be used in case of server side problems (i.e., HTTP 5xx).
 */
@JsonDeserialize // Override inherited @JsonDeserialize annotation
public class ServerProblem extends AbstractThrowableProblem {

	public ServerProblem(final StatusType status, final String detail) {
		super(Problem.DEFAULT_TYPE, status.getReasonPhrase(), status, detail);
	}
}
