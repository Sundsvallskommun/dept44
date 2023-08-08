package se.sundsvall.dept44.configuration.webservicetemplate.exception;

public class WebserviceTemplateException extends RuntimeException{

	public WebserviceTemplateException(String message) {
		super(message);
	}

	public WebserviceTemplateException(String message, Throwable cause) {
		super(message, cause);
	}
}
