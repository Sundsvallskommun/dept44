package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

/**
 * Logbook view of an outgoing response that copies at most what the {@link BodyCapturePolicy} allows of the body.
 * <p>
 * Whether the body is copied at all is decided when its first byte or character is written, once its status, content
 * type, content disposition and often its length are known, and again after {@link #reset()}. While it is written, the
 * copy stops as soon as the body grows past the limit, and the body is logged as omitted. The client always receives
 * the whole body, and the container's own writer is used, so its character encoding and buffer handling apply as
 * usual.
 */
final class CapturingResponse extends HttpServletResponseWrapper implements HttpResponse {

	private static final byte[] EMPTY = new byte[0];

	private final BodyCapturePolicy policy;
	private final String protocolVersion;
	private final IntPredicate capturesStatus;
	private boolean withBody;
	private boolean decided;
	private boolean failed;
	private BoundedCapture capture;
	private ServletOutputStream outputStream;
	private PrintWriter writer;

	CapturingResponse(final HttpServletResponse response, final BodyCapturePolicy policy, final String protocolVersion) {
		this(response, policy, protocolVersion, status -> true);
	}

	/**
	 * @param capturesStatus which response statuses a body is captured for, checked when the body starts being written
	 */
	CapturingResponse(final HttpServletResponse response, final BodyCapturePolicy policy, final String protocolVersion, final IntPredicate capturesStatus) {
		super(response);
		this.policy = policy;
		this.protocolVersion = protocolVersion;
		this.capturesStatus = capturesStatus;
	}

	/**
	 * Takes effect only before the body starts being written: bytes already sent cannot be captured afterwards.
	 */
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
			outputStream = new CopyingOutputStream(super.getOutputStream());
		}
		return outputStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (writer == null) {
			final var original = super.getWriter();
			// The container settles the character encoding when its writer is obtained
			writer = new PrintWriter(new CopyingWriter(original, getCharset()));
		}
		return writer;
	}

	@Override
	public void resetBuffer() {
		super.resetBuffer();
		Optional.ofNullable(capture).ifPresent(BoundedCapture::reset);
	}

	/**
	 * Also clears the headers, so whether the body is copied is decided again when the new body starts being written.
	 */
	@Override
	public void reset() {
		super.reset();
		capture = null;
		decided = false;
	}

	/**
	 * Marks the request as failed with an exception that escaped the filter chain. The container answers such a request
	 * with status 500 unless the response is already committed, so that is the status logged; the response itself is
	 * left as it is.
	 */
	void failed() {
		failed = true;
	}

	@Override
	public int getStatus() {
		if (failed && !isCommitted()) {
			return SC_INTERNAL_SERVER_ERROR;
		}
		return super.getStatus();
	}

	private BoundedCapture capture() {
		if (!decided) {
			decided = true;
			final var contentLength = BodyCapturePolicy.parseLength(getHeader(CONTENT_LENGTH));
			if (withBody && capturesStatus.test(getStatus()) && policy.allowsCapture(getContentType(), getHeaders(CONTENT_DISPOSITION), contentLength)) {
				capture = new BoundedCapture(policy);
			}
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
	private final class CopyingOutputStream extends ServletOutputStream {

		private final ServletOutputStream original;

		private CopyingOutputStream(final ServletOutputStream original) {
			this.original = original;
		}

		@Override
		public void write(final int b) throws IOException {
			original.write(b);
			Optional.ofNullable(capture()).ifPresent(copy -> copy.write(b));
		}

		@Override
		public void write(final byte[] bytes, final int offset, final int length) throws IOException {
			original.write(bytes, offset, length);
			Optional.ofNullable(capture()).ifPresent(copy -> copy.write(bytes, offset, length));
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

	/**
	 * Writes everything to the container's writer, which keeps its own buffer, and copies the encoded characters into
	 * the capture for as long as it accepts bytes. It buffers nothing itself, so a reset of the response discards
	 * everything written before it.
	 */
	private final class CopyingWriter extends Writer {

		private final PrintWriter original;
		private final Charset charset;

		private CopyingWriter(final PrintWriter original, final Charset charset) {
			this.original = original;
			this.charset = charset;
		}

		@Override
		public void write(final int c) {
			original.write(c);
			copy(String.valueOf((char) c));
		}

		@Override
		public void write(final char[] chars, final int offset, final int length) {
			original.write(chars, offset, length);
			copy(new String(chars, offset, length));
		}

		@Override
		public void write(final String string, final int offset, final int length) {
			original.write(string, offset, length);
			copy(string.substring(offset, offset + length));
		}

		@Override
		public void flush() {
			original.flush();
		}

		@Override
		public void close() {
			original.close();
		}

		private void copy(final String characters) {
			Optional.ofNullable(capture()).ifPresent(copy -> {
				final var bytes = characters.getBytes(charset);
				copy.write(bytes, 0, bytes.length);
			});
		}
	}
}
