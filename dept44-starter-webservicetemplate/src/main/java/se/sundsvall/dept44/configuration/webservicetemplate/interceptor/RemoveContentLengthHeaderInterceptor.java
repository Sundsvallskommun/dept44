package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

public class RemoveContentLengthHeaderInterceptor implements HttpRequestInterceptor {

	@Override
	public void process(final HttpRequest request, final EntityDetails entityDetails, final HttpContext httpContext) {
		request.removeHeaders(HttpHeaders.CONTENT_LENGTH);
	}
}
