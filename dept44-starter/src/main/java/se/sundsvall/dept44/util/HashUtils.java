package se.sundsvall.dept44.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Content hashing utilities.
 *
 * <p>
 * The hash produced by this class is the <b>lower-case hex encoding of the SHA-256 digest over the raw bytes</b> of the
 * content. It is byte-for-byte identical to what MariaDB/MySQL produces with {@code lower(sha2(content, 256))}, which
 * makes the application-side and database-side hashes interchangeable - the same file yields the same hash regardless
 * of which service computed it.
 */
public final class HashUtils {

	private static final String SHA_256 = "SHA-256";

	private HashUtils() {}

	/**
	 * Computes the SHA-256 hash of the given content.
	 *
	 * @param  content the content to hash
	 * @return         the hash, lower-case hex encoded
	 */
	public static String sha256Hex(final byte[] content) {
		return HexFormat.of().formatHex(newDigest().digest(content));
	}

	/**
	 * Computes the SHA-256 hash of the given content by streaming it through a {@link DigestInputStream}, without
	 * materializing it in memory. The stream is fully consumed and closed.
	 *
	 * @param  content     the content to hash
	 * @return             the hash, lower-case hex encoded
	 * @throws IOException if the content couldn't be read
	 */
	public static String sha256Hex(final InputStream content) throws IOException {
		final var digest = newDigest();
		try (final var digestInputStream = new DigestInputStream(content, digest)) {
			digestInputStream.transferTo(OutputStream.nullOutputStream());
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance(SHA_256);
		} catch (final NoSuchAlgorithmException e) {
			// Every Java platform is required to support SHA-256, so this cannot happen.
			throw new IllegalStateException("%s is not available".formatted(SHA_256), e);
		}
	}
}
