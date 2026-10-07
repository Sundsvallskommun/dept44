package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Response;
import java.nio.charset.Charset;
import java.util.Optional;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static se.sundsvall.dept44.configuration.feign.logbook.FeignLogbookRequest.toLogbookHeaders;

/**
 * Logbook view of a Feign response, with the body that has been read for logging, if any.
 */
final class FeignLogbookResponse implements HttpResponse {

	private final int status;
	private final HttpHeaders headers;
	private final byte[] body;
	private final Charset charset;
	private boolean withBody;

	private FeignLogbookResponse(final int status, final HttpHeaders headers, final byte[] body, final Charset charset) {
		this.status = status;
		this.headers = headers;
		this.body = body;
		this.charset = charset;
	}

	static FeignLogbookResponse create(final Response response, final byte[] body) {
		return new FeignLogbookResponse(response.status(), toLogbookHeaders(response.headers()), body, response.charset());
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public String getProtocolVersion() {
		return "HTTP/1.1";
	}

	@Override
	public Origin getOrigin() {
		return Origin.REMOTE;
	}

	@Override
	public HttpHeaders getHeaders() {
		return headers;
	}

	@Override
	public String getContentType() {
		return headers.getFirst(CONTENT_TYPE);
	}

	@Override
	public Charset getCharset() {
		return Optional.ofNullable(charset).orElse(UTF_8);
	}

	@Override
	public HttpResponse withBody() {
		withBody = true;
		return this;
	}

	@Override
	public HttpResponse withoutBody() {
		withBody = false;
		return this;
	}

	@Override
	public byte[] getBody() {
		if (withBody && body != null) {
			return body;
		}
		return new byte[0];
	}
}
