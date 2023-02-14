package se.sundsvall.dept44.common.validators.annotation.exception;

/**
 * Exception that will be thrown if the used annotation does not contain required methods.
 */
public class IncompatibleAnnotationException extends RuntimeException {
	private static final long serialVersionUID = 5071957184294984636L;

	/**
	 * Creates a new IncompatibleAnnotationException.
	 *
	 * @param message the exception message.
	 */
	public IncompatibleAnnotationException(final String message) {
		super(message);
	}
}
