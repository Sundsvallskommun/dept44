package se.sundsvall.dept44.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class EncodingUtils {

	private EncodingUtils() {}

	/**
	 * Removes double encoded content.
	 *
	 * If a String contains characters like: "Ã
	 * ÃÃÃ¥Ã¤Ã¶", it might be double encoded.
	 * By running it through this method, it will become correctly UTF-8 encoded again.
	 *
	 * @param  string String to fix
	 * @return        the corrected string
	 */
	public static String fixDoubleEncodedUTF8Content(final String string) {
		if (isDoubleEncodedUTF8Content(string)) {
			// Fix the string by assuming it is ASCII extended and recode it once.
			return new String(string.getBytes(ISO_8859_1), UTF_8);
		}
		return string;
	}

	/**
	 * Check if a string contains double encoded UTF-8 content.
	 *
	 * If a String contains characters like: "Ã
	 * ÃÃÃ¥Ã¤Ã¶", it might be double encoded.
	 * This method will detect that.
	 *
	 * @param  string content to check
	 * @return        true if the string content is double encoded, false otherwise.
	 */
	public static boolean isDoubleEncodedUTF8Content(final String string) {
		// Interpret the string as UTF-8
		final var bytes = string.getBytes(UTF_8);

		// Now check if the bytes contain 0x83 0xC2, meaning double encoded garbage.
		for (var i = 0; i < bytes.length; i++) {
			if (bytes[i] == -125 && i + 1 < bytes.length && bytes[i + 1] == -62) {
				return true;
			}
		}
		return false;
	}
}
