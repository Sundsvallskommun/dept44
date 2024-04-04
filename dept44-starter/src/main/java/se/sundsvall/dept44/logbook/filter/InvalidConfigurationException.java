package se.sundsvall.dept44.logbook.filter;

public class InvalidConfigurationException extends RuntimeException {

	private static final long serialVersionUID = 4272111429378434943L;

	public InvalidConfigurationException(String message, Exception cause) {
		super(message, cause);
	}

	public InvalidConfigurationException(String message) {
		super(message);
	}

	public InvalidConfigurationException(Exception cause) {
		super(cause);
	}
}
