package se.sundsvall.dept44.problem;

import java.net.URI;

/**
 * Abstract base class for throwable problems.
 * Extend this class to create custom problem types like ClientProblem or ServerProblem.
 */
public abstract class AbstractThrowableProblem extends ThrowableProblem {

	/**
	 * Create a new AbstractThrowableProblem with all fields.
	 *
	 * @param type     the problem type URI
	 * @param title    the problem title
	 * @param status   the HTTP status
	 * @param detail   the problem detail
	 * @param instance the problem instance URI
	 * @param cause    the cause of this problem
	 */
	protected AbstractThrowableProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance, final ThrowableProblem cause) {
		super(type, title, status, detail, instance, cause);
	}

	/**
	 * Create a new AbstractThrowableProblem without cause.
	 *
	 * @param type     the problem type URI
	 * @param title    the problem title
	 * @param status   the HTTP status
	 * @param detail   the problem detail
	 * @param instance the problem instance URI
	 */
	protected AbstractThrowableProblem(final URI type, final String title, final StatusType status, final String detail, final URI instance) {
		super(type, title, status, detail, instance, null);
	}

	/**
	 * Create a new AbstractThrowableProblem without instance and cause.
	 *
	 * @param type   the problem type URI
	 * @param title  the problem title
	 * @param status the HTTP status
	 * @param detail the problem detail
	 */
	protected AbstractThrowableProblem(final URI type, final String title, final StatusType status, final String detail) {
		super(type, title, status, detail, null, null);
	}

	/**
	 * Create a new AbstractThrowableProblem with minimal fields.
	 *
	 * @param type   the problem type URI
	 * @param title  the problem title
	 * @param status the HTTP status
	 */
	protected AbstractThrowableProblem(final URI type, final String title, final StatusType status) {
		super(type, title, status, null, null, null);
	}
}
