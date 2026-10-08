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
import org.springframework.web.util.UriComponentsBuilder;
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

	private static final String DEFAULT_PROTOCOL_VERSION = "HTTP/1.1";

	private final URI uri;
	private final String method;
	private final HttpHeaders headers;
	private final byte[] body;
	private final Charset charset;
	private final String protocolVersion;
	private boolean withBody;

	private FeignLogbookRequest(final URI uri, final String method, final HttpHeaders headers, final byte[] body, final Charset charset,
		final String protocolVersion) {
		this.uri = uri;
		this.method = method;
		this.headers = headers;
		this.body = body;
		this.charset = charset;
		this.protocolVersion = protocolVersion;
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
		return new FeignLogbookRequest(toUri(request.url()), request.httpMethod().name(), headers, body, request.charset(),
			toProtocolVersion(request.protocolVersion()));
	}

	/**
	 * {@link URI} rejects some characters that HTTP clients send as they are, such as a space or an unresolved template
	 * variable. Logging must never fail the request, so such a URL is logged with those characters encoded, and a URL
	 * that cannot be parsed at all is logged without it.
	 */
	static URI toUri(final String url) {
		try {
			return URI.create(url);
		} catch (final IllegalArgumentException _) {
			return toEncodedUri(url);
		}
	}

	private static URI toEncodedUri(final String url) {
		try {
			return UriComponentsBuilder.fromUriString(url).build().encode().toUri();
		} catch (final RuntimeException _) {
			return URI.create("");
		}
	}

	/**
	 * Feign reports the protocol version: for a request the one it intends to use, for a response the one the HTTP client
	 * actually negotiated.
	 */
	static String toProtocolVersion(final Request.ProtocolVersion protocolVersion) {
		return Optional.ofNullable(protocolVersion)
			.map(Request.ProtocolVersion::toString)
			.orElse(DEFAULT_PROTOCOL_VERSION);
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
		return protocolVersion;
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
