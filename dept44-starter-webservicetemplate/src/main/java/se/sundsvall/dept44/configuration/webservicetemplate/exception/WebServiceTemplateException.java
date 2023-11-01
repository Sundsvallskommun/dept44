package se.sundsvall.dept44.configuration.webservicetemplate.exception;

public class WebServiceTemplateException extends RuntimeException {

	private static final long serialVersionUID = 4286960975149857805L;

	public WebServiceTemplateException(String message) {
		super(message);
	}

	public WebServiceTemplateException(String message, Throwable cause) {
		super(message, cause);
	}
}
