package se.sundsvall.dept44.logbook;

import java.io.IOException;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.SecurityStrategy;

/**
 * Same as Logbook's {@link SecurityStrategy} (logs only requests rejected with 401 or 403), but without capturing the
 * response body.
 * <p>
 * The security filter runs for every request, while only rejected requests are ever written. With Logbook's own
 * strategy every response body is therefore copied into memory once more, on top of the copy made by the regular
 * payload logging filter, only to be thrown away.
 */
public final class BodylessSecurityStrategy implements Strategy {

	private final SecurityStrategy delegate = new SecurityStrategy();

	@Override
	public HttpRequest process(final HttpRequest request) {
		return delegate.process(request);
	}

	@Override
	public void write(final Precorrelation precorrelation, final HttpRequest request, final Sink sink) {
		delegate.write(precorrelation, request, sink);
	}

	@Override
	public HttpResponse process(final HttpRequest request, final HttpResponse response) {
		return response.withoutBody();
	}

	@Override
	public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response, final Sink sink) throws IOException {
		delegate.write(correlation, request, response, sink);
	}
}
