package se.sundsvall.dept44.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class HashUtilsTest {

	/**
	 * The two canonical SHA-256 test vectors from FIPS 180-4 / RFC 6234. If either of these ever changes, the hash
	 * contract is broken and every already-stored hash in every consuming service is invalidated.
	 */
	private static final String HASH_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
	private static final String HASH_OF_EMPTY_CONTENT = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

	@Test
	void sha256HexFromBytes() {
		assertThat(HashUtils.sha256Hex("abc".getBytes(UTF_8))).isEqualTo(HASH_OF_ABC);
	}

	@Test
	void sha256HexFromEmptyBytes() {
		assertThat(HashUtils.sha256Hex(new byte[0])).isEqualTo(HASH_OF_EMPTY_CONTENT);
	}

	@Test
	void sha256HexFromStream() throws IOException {
		assertThat(HashUtils.sha256Hex(new ByteArrayInputStream("abc".getBytes(UTF_8)))).isEqualTo(HASH_OF_ABC);
	}

	@Test
	void sha256HexFromEmptyStream() throws IOException {
		assertThat(HashUtils.sha256Hex(new ByteArrayInputStream(new byte[0]))).isEqualTo(HASH_OF_EMPTY_CONTENT);
	}

	/**
	 * Content larger than a single transfer buffer, to prove the digest is fed every chunk rather than just the first
	 * read. A truncated digest would still produce a well-formed 64 character hash, so only comparing against the
	 * single-shot overload catches it.
	 */
	@Test
	void sha256HexFromStreamMatchesBytesForMultiChunkContent() throws IOException {
		final var content = new byte[256 * 1024];
		new Random(1L).nextBytes(content);

		assertThat(HashUtils.sha256Hex(new ByteArrayInputStream(content))).isEqualTo(HashUtils.sha256Hex(content));
	}

	@Test
	void sha256HexHandlesAllByteValues() throws IOException {
		final var content = new byte[256];
		for (var i = 0; i < content.length; i++) {
			content[i] = (byte) i;
		}

		assertThat(HashUtils.sha256Hex(content))
			.isEqualTo(HashUtils.sha256Hex(new ByteArrayInputStream(content)))
			.matches("[0-9a-f]{64}");
	}

	@Test
	void sha256HexClosesStream() throws IOException {
		final var stream = new ClosableSpyInputStream("abc".getBytes(UTF_8));

		HashUtils.sha256Hex(stream);

		assertThat(stream.closed).isTrue();
	}

	@Test
	void sha256HexPropagatesIOException() {
		final var stream = new InputStream() {

			@Override
			public int read() throws IOException {
				throw new IOException("nope");
			}
		};

		assertThatThrownBy(() -> HashUtils.sha256Hex(stream))
			.isInstanceOf(IOException.class)
			.hasMessage("nope");
	}

	/**
	 * SHA-256 is mandatory on every Java platform, so the missing-algorithm path is unreachable in practice and can only
	 * be exercised by stubbing the JDK call.
	 */
	@Test
	void sha256HexThrowsIllegalStateExceptionWhenAlgorithmIsMissing() {
		final var cause = new NoSuchAlgorithmException("no such thing");

		try (final var messageDigestMock = mockStatic(MessageDigest.class)) {
			messageDigestMock.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(cause);

			assertThatThrownBy(() -> HashUtils.sha256Hex(new byte[0]))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SHA-256 is not available")
				.hasCause(cause);
		}
	}

	private static final class ClosableSpyInputStream extends ByteArrayInputStream {

		private boolean closed;

		private ClosableSpyInputStream(final byte[] content) {
			super(content);
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
