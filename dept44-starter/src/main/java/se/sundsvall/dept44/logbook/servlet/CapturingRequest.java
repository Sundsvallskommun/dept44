package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Optional;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Logbook view of an incoming request that never holds more of the body in memory than the {@link BodyCapturePolicy}
 * allows.
 * <p>
 * When Logbook asks for the body, at most the allowed size plus one byte is read. A body that fits is kept for the log;
 * a larger one is logged as omitted. Either way the application reads the whole body, starting with the bytes already
 * read. Form and multipart bodies are never read here: the servlet container parses them from the raw stream.
 */
final class CapturingRequest extends HttpServletRequestWrapper implements HttpRequest {

	private static final byte[] EMPTY = new byte[0];

	private final BodyCapturePolicy policy;
	private boolean withBody;
	private boolean oversized;
	private byte[] captured;
	private ServletInputStream replay;
	private BufferedReader reader;

	CapturingRequest(final HttpServletRequest request, final BodyCapturePolicy policy) {
		super(request);
		this.policy = policy;
	}

	@Override
	public HttpRequest withBody() throws IOException {
		withBody = true;
		if (replay == null && isReadable()) {
			read();
		}
		return this;
	}

	@Override
	public HttpRequest withoutBody() {
		withBody = false;
		return this;
	}

	@Override
	public byte[] getBody() {
		if (!withBody) {
			return EMPTY;
		}
		if (oversized) {
			return policy.omittedNote(getContentType()).getBytes(getCharset());
		}
		return Optional.ofNullable(captured).orElse(EMPTY);
	}

	private boolean isReadable() {
		return !BodyCapturePolicy.isParsedByContainer(getContentType()) && !policy.exceedsLimit(getContentLengthLong());
	}

	private void read() throws IOException {
		final var body = super.getInputStream();
		final byte[] head;
		if (policy.isLimited()) {
			head = body.readNBytes(policy.readLimit());
		} else {
			head = body.readAllBytes();
		}

		if (policy.exceedsLimit(head.length)) {
			oversized = true;
		} else {
			captured = head;
		}
		replay = new ReplayingInputStream(new SequenceInputStream(new ByteArrayInputStream(head), body));
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		if (replay == null) {
			return super.getInputStream();
		}
		return replay;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		if (replay == null) {
			return super.getReader();
		}
		if (reader == null) {
			reader = new BufferedReader(new InputStreamReader(replay, getCharset()));
		}
		return reader;
	}

	@Override
	public String getRemote() {
		return getRemoteAddr();
	}

	@Override
	public String getHost() {
		return getServerName();
	}

	@Override
	public Optional<Integer> getPort() {
		return Optional.of(getServerPort());
	}

	@Override
	public String getPath() {
		return getRequestURI();
	}

	@Override
	public String getQuery() {
		return Optional.ofNullable(getQueryString()).orElse("");
	}

	@Override
	public String getProtocolVersion() {
		return getProtocol();
	}

	@Override
	public Origin getOrigin() {
		return Origin.REMOTE;
	}

	@Override
	public HttpHeaders getHeaders() {
		var headers = HttpHeaders.empty();
		for (final var name : Collections.list(getHeaderNames())) {
			headers = headers.update(name, Collections.list(getHeaders(name)));
		}
		return headers;
	}

	@Override
	public Charset getCharset() {
		try {
			return Optional.ofNullable(getCharacterEncoding())
				.map(Charset::forName)
				.orElse(ISO_8859_1);
		} catch (final IllegalArgumentException _) {
			return ISO_8859_1;
		}
	}

	/**
	 * Serves the request body from a stream that starts with the bytes already read for logging.
	 */
	private static final class ReplayingInputStream extends ServletInputStream {

		private final InputStream body;
		private boolean finished;

		private ReplayingInputStream(final InputStream body) {
			this.body = body;
		}

		@Override
		public int read() throws IOException {
			return track(body.read());
		}

		@Override
		public int read(final byte[] buffer, final int offset, final int length) throws IOException {
			return track(body.read(buffer, offset, length));
		}

		@Override
		public boolean isFinished() {
			return finished;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(final ReadListener readListener) {
			throw new UnsupportedOperationException("Non-blocking reads are not supported for a request body read for logging");
		}

		@Override
		public void close() throws IOException {
			body.close();
		}

		private int track(final int result) {
			if (result == -1) {
				finished = true;
			}
			return result;
		}
	}
}
