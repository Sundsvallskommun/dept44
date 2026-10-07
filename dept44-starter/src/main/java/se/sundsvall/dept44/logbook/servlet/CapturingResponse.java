package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

/**
 * Logbook view of an outgoing response that copies at most what the {@link BodyCapturePolicy} allows of the body.
 * <p>
 * Whether the body is copied at all is decided when it starts being written, once its content type, content
 * disposition and often its length are known. While it is written, the copy stops as soon as the body grows past the
 * limit, and the body is logged as omitted. The client always receives the whole body.
 */
final class CapturingResponse extends HttpServletResponseWrapper implements HttpResponse {

	private static final byte[] EMPTY = new byte[0];

	private final BodyCapturePolicy policy;
	private final String protocolVersion;
	private boolean withBody;
	private BoundedCapture capture;
	private ServletOutputStream outputStream;
	private PrintWriter writer;

	CapturingResponse(final HttpServletResponse response, final BodyCapturePolicy policy, final String protocolVersion) {
		super(response);
		this.policy = policy;
		this.protocolVersion = protocolVersion;
	}

	@Override
	public HttpResponse withBody() {
		withBody = true;
		return this;
	}

	@Override
	public HttpResponse withoutBody() {
		withBody = false;
		capture = null;
		return this;
	}

	@Override
	public byte[] getBody() {
		if (!withBody || capture == null) {
			return EMPTY;
		}
		if (capture.isOverflowed()) {
			return policy.omittedNote(getContentType()).getBytes(getCharset());
		}
		return capture.toByteArray();
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (outputStream == null) {
			outputStream = new CopyingOutputStream(super.getOutputStream(), startCapture());
		}
		return outputStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (writer == null) {
			writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), getCharset()));
		}
		return writer;
	}

	@Override
	public void flushBuffer() throws IOException {
		if (writer != null) {
			writer.flush();
		}
		super.flushBuffer();
	}

	@Override
	public void resetBuffer() {
		super.resetBuffer();
		Optional.ofNullable(capture).ifPresent(BoundedCapture::reset);
	}

	@Override
	public void reset() {
		super.reset();
		Optional.ofNullable(capture).ifPresent(BoundedCapture::reset);
	}

	private BoundedCapture startCapture() {
		final var contentLength = BodyCapturePolicy.parseLength(getHeader(CONTENT_LENGTH));
		if (withBody && policy.allowsCapture(getContentType(), getHeaders(CONTENT_DISPOSITION), contentLength)) {
			capture = new BoundedCapture(policy);
		}
		return capture;
	}

	@Override
	public String getProtocolVersion() {
		return protocolVersion;
	}

	@Override
	public Origin getOrigin() {
		return Origin.LOCAL;
	}

	/**
	 * While an asynchronous response is completed on another thread, the servlet container can briefly report a header
	 * without a name. Such headers are left out rather than failing the log line.
	 */
	@Override
	public HttpHeaders getHeaders() {
		var headers = HttpHeaders.empty();
		for (final var name : getHeaderNames()) {
			if (name != null) {
				headers = headers.update(name, getHeaders(name).stream().filter(Objects::nonNull).toList());
			}
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
	 * Writes everything to the client, and copies into the capture for as long as it accepts bytes.
	 */
	private static final class CopyingOutputStream extends ServletOutputStream {

		private final ServletOutputStream original;
		private final BoundedCapture capture;

		private CopyingOutputStream(final ServletOutputStream original, final BoundedCapture capture) {
			this.original = original;
			this.capture = capture;
		}

		@Override
		public void write(final int b) throws IOException {
			original.write(b);
			if (capture != null) {
				capture.write(b);
			}
		}

		@Override
		public void write(final byte[] bytes, final int offset, final int length) throws IOException {
			original.write(bytes, offset, length);
			if (capture != null) {
				capture.write(bytes, offset, length);
			}
		}

		@Override
		public void flush() throws IOException {
			original.flush();
		}

		@Override
		public void close() throws IOException {
			original.close();
		}

		@Override
		public boolean isReady() {
			return original.isReady();
		}

		@Override
		public void setWriteListener(final WriteListener writeListener) {
			original.setWriteListener(writeListener);
		}
	}
}
