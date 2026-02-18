package se.sundsvall.dept44.exception;

import org.springframework.http.HttpStatus;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A ThrowableProblem to be used in case of client side problems (i.e., HTTP 4xx).
 */
@JsonDeserialize // Override inherited @JsonDeserialize annotation
public class ClientProblem extends ThrowableProblem {

	public ClientProblem(final HttpStatus status, final String detail) {
		super(Problem.DEFAULT_TYPE, status.getReasonPhrase(), status, detail, null);
	}
}
