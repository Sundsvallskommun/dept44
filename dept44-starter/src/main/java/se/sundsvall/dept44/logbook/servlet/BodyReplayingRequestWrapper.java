package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Request wrapper that serves the body from the given stream, for a request whose body has already been partly read.
 */
final class BodyReplayingRequestWrapper extends HttpServletRequestWrapper {

	private final ServletInputStream inputStream;
	private BufferedReader reader;

	BodyReplayingRequestWrapper(final HttpServletRequest request, final InputStream body) {
		super(request);
		this.inputStream = new InputStreamAdapter(body);
	}

	@Override
	public ServletInputStream getInputStream() {
		return inputStream;
	}

	@Override
	public BufferedReader getReader() {
		if (reader == null) {
			final var charset = Optional.ofNullable(getCharacterEncoding())
				.map(Charset::forName)
				.orElse(ISO_8859_1);
			reader = new BufferedReader(new InputStreamReader(inputStream, charset));
		}
		return reader;
	}

	private static final class InputStreamAdapter extends ServletInputStream {

		private final InputStream body;
		private boolean finished;

		private InputStreamAdapter(final InputStream body) {
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
			throw new UnsupportedOperationException("Non-blocking reads are not supported for a replayed request body");
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
