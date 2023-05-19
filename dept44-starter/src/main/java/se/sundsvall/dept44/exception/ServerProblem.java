package se.sundsvall.dept44.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.StatusType;

/**
 * A ThrowableProblem to be used in case in case of server side problems (i.e. HTTP 5xx).
 */
public class ServerProblem extends AbstractThrowableProblem {

	private static final long serialVersionUID = 3240800702346463958L;

	public ServerProblem(StatusType status, String detail) {
		super(DEFAULT_TYPE, status.getReasonPhrase(), status, detail);
	}
}
