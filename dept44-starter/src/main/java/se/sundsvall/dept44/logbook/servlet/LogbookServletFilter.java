package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static jakarta.servlet.DispatcherType.REQUEST;

/**
 * Runs Logbook's servlet filter with the payload capture limits of {@link BodyCapturePolicy} enforced on both sides:
 * <ul>
 * <li>a request body of unknown length is measured (see {@link UnknownLengthRequestInspector}) before Logbook decides
 * whether to buffer it, and</li>
 * <li>the response handed down the chain is wrapped in a {@link BodyCaptureResponseWrapper}, directly inside Logbook,
 * so that a binary, attachment or oversized response body is never copied into memory.</li>
 * </ul>
 */
public final class LogbookServletFilter implements Filter {

	private final Filter logbookFilter;
	private final BodyCapturePolicy policy;

	public LogbookServletFilter(final Filter logbookFilter, final BodyCapturePolicy policy) {
		this.logbookFilter = logbookFilter;
		this.policy = policy;
	}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
		if (!(request instanceof final HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse)) {
			chain.doFilter(request, response);
			return;
		}

		var inspectedRequest = httpRequest;
		if (httpRequest.getDispatcherType() == REQUEST) {
			inspectedRequest = UnknownLengthRequestInspector.inspect(httpRequest, policy);
		}

		logbookFilter.doFilter(inspectedRequest, response, (innerRequest, innerResponse) -> chain.doFilter(innerRequest,
			new BodyCaptureResponseWrapper((HttpServletResponse) innerResponse, policy)));
	}
}
