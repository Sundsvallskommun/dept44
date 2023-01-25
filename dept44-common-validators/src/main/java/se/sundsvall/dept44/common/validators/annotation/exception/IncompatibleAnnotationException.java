package se.sundsvall.dept44.common.validators.annotation.exception;

public class IncompatibleAnnotationException extends RuntimeException {
	private static final long serialVersionUID = 5071957184294984636L;
	
	public IncompatibleAnnotationException(String message) {
		super(message);
	}
}
