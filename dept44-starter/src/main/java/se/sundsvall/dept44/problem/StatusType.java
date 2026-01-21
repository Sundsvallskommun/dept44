package se.sundsvall.dept44.problem;

import java.io.Serializable;

/**
 * Represents an HTTP status code with its reason phrase.
 */
public interface StatusType extends Serializable {

	/**
	 * Get the HTTP status code.
	 *
	 * @return the status code
	 */
	int getStatusCode();

	/**
	 * Get the reason phrase for this status.
	 *
	 * @return the reason phrase
	 */
	String getReasonPhrase();
}
