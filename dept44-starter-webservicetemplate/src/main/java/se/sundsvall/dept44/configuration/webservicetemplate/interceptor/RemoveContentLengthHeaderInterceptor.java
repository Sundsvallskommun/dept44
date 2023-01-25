package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

public class RemoveContentLengthHeaderInterceptor implements HttpRequestInterceptor {

	@Override
	public void process(HttpRequest request, HttpContext context) {
		request.removeHeaders(HTTP.CONTENT_LEN);
	}
}
