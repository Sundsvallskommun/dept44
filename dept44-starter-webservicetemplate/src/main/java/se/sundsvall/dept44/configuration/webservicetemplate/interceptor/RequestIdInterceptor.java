package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import se.sundsvall.dept44.requestid.RequestId;

public class RequestIdInterceptor implements HttpRequestInterceptor {

	@Override
	public void process(HttpRequest request, HttpContext context) {
		request.addHeader(RequestId.HEADER_NAME, RequestId.get());
	}
}
