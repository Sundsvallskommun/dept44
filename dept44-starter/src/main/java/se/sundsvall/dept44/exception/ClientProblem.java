package se.sundsvall.dept44.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.StatusType;

/**
 * A ThrowableProblem to be used in case in case of client side problems (i.e. HTTP 4xx).
 */
public class ClientProblem extends AbstractThrowableProblem {

	private static final long serialVersionUID = -809117800020038315L;

	public ClientProblem(StatusType status, String detail) {
		super(DEFAULT_TYPE, status.getReasonPhrase(), status, detail);
	}
}
