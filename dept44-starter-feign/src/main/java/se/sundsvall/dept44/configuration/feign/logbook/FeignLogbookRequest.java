package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Request;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Origin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * Logbook view of an outgoing Feign request.
 */
final class FeignLogbookRequest implements HttpRequest {

	private final URI uri;
	private final String method;
	private final HttpHeaders headers;
	private final byte[] body;
	private final Charset charset;
	private boolean withBody;

	private FeignLogbookRequest(final URI uri, final String method, final HttpHeaders headers, final byte[] body, final Charset charset) {
		this.uri = uri;
		this.method = method;
		this.headers = headers;
		this.body = body;
		this.charset = charset;
	}

	/**
	 * The body of a Feign request is already in memory. Its length is added as Content-Length when Feign has not set
	 * one, which is also what the HTTP client sends, so that the capture decision can be made from the headers.
	 */
	static FeignLogbookRequest create(final Request request) {
		final var body = request.body();
		var headers = toLogbookHeaders(request.headers());
		if (body != null && !headers.containsKey(CONTENT_LENGTH)) {
			headers = headers.update(CONTENT_LENGTH, String.valueOf(body.length));
		}
		return new FeignLogbookRequest(URI.create(request.url()), request.httpMethod().name(), headers, body, request.charset());
	}

	static HttpHeaders toLogbookHeaders(final Map<String, Collection<String>> headers) {
		final Map<String, List<String>> converted = new HashMap<>();
		Optional.ofNullable(headers).ifPresent(values -> values.forEach((name, value) -> converted.put(name, new ArrayList<>(value))));
		return HttpHeaders.of(converted);
	}

	@Override
	public String getRemote() {
		return "localhost";
	}

	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public String getScheme() {
		return Optional.ofNullable(uri.getScheme()).orElse("");
	}

	@Override
	public String getHost() {
		return Optional.ofNullable(uri.getHost()).orElse("");
	}

	@Override
	public Optional<Integer> getPort() {
		return Optional.of(uri.getPort()).filter(port -> port != -1);
	}

	@Override
	public String getPath() {
		return Optional.ofNullable(uri.getPath()).orElse("");
	}

	@Override
	public String getQuery() {
		return Optional.ofNullable(uri.getQuery()).orElse("");
	}

	@Override
	public String getProtocolVersion() {
		return "HTTP/1.1";
	}

	@Override
	public Origin getOrigin() {
		return Origin.LOCAL;
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
	public HttpRequest withBody() {
		withBody = true;
		return this;
	}

	@Override
	public HttpRequest withoutBody() {
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
