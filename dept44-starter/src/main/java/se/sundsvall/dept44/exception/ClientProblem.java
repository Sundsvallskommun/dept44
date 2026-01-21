package se.sundsvall.dept44.exception;

import se.sundsvall.dept44.problem.AbstractThrowableProblem;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.StatusType;

/**
 * A ThrowableProblem to be used in case of client side problems (i.e., HTTP 4xx).
 */
public class ClientProblem extends AbstractThrowableProblem {

	public ClientProblem(final StatusType status, final String detail) {
		super(Problem.DEFAULT_TYPE, status.getReasonPhrase(), status, detail);
	}
}
