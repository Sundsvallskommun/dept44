package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import se.sundsvall.dept44.requestid.RequestId;

public class RequestIdInterceptor implements HttpRequestInterceptor {

	@Override
	public void process(final HttpRequest request, final EntityDetails entityDetails, final HttpContext context) {
		request.addHeader(RequestId.HEADER_NAME, RequestId.get());
	}
}
