package se.sundsvall.dept44.configuration.webservicetemplate.exception;

public class WebServiceTemplateException extends RuntimeException{

	public WebServiceTemplateException(String message) {
		super(message);
	}

	public WebServiceTemplateException(String message, Throwable cause) {
		super(message, cause);
	}
}
