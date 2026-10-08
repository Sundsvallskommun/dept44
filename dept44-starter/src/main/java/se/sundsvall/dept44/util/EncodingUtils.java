package se.sundsvall.dept44.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class EncodingUtils {

	/**
	 * A UTF-8 encoded character that was decoded as ISO-8859-1: one character per byte, a lead byte followed by as many
	 * continuation bytes (0x80-0xBF) as the lead byte announces.
	 */
	private static final Pattern DOUBLE_ENCODED_CHARACTER = Pattern.compile(
		"[\\u00C2-\\u00DF][\\u0080-\\u00BF]|[\\u00E0-\\u00EF][\\u0080-\\u00BF]{2}|[\\u00F0-\\u00F4][\\u0080-\\u00BF]{3}");

	private EncodingUtils() {}

	/**
	 * Removes double encoded content.
	 *
	 * If a String contains characters like: "Ã
	 * ÃÃÃ¥Ã¤Ã¶", it might be double encoded.
	 * By running it through this method, it will become correctly UTF-8 encoded again. A string is only changed when it
	 * shows clear signs of double encoding (see {@link #isDoubleEncodedUTF8Content(String)}). Each double encoded
	 * character is then repaired on its own, so correctly encoded characters in the same string (such as Cyrillic, or a
	 * correct "å") are kept as they are.
	 *
	 * @param  string String to fix
	 * @return        the corrected string
	 */
	public static String fixDoubleEncodedUTF8Content(final String string) {
		if (!isDoubleEncodedUTF8Content(string)) {
			return string;
		}
		return DOUBLE_ENCODED_CHARACTER.matcher(string)
			.replaceAll(match -> Matcher.quoteReplacement(decode(match.group()).orElse(match.group())));
	}

	/**
	 * Check if a string contains double encoded UTF-8 content.
	 *
	 * If a String contains characters like: "Ã
	 * ÃÃÃ¥Ã¤Ã¶", it might be double encoded.
	 * This method will detect that.
	 * <p>
	 * Many correctly encoded strings also hold characters that happen to form a valid UTF-8 byte sequence, such as "Å"
	 * followed by a non-breaking space. So only a sequence that ordinary text does not contain counts: one that starts
	 * with "Â" or "Ã" (a double encoded Latin-1 character, such as "å" or "»"), or one with a C1 control character
	 * (U+0080-U+009F) in it (most other double encoded characters, such as "€" or Cyrillic letters).
	 *
	 * @param  string content to check
	 * @return        true if the string content is double encoded, false otherwise.
	 */
	public static boolean isDoubleEncodedUTF8Content(final String string) {
		if (string == null) {
			return false;
		}
		final var matcher = DOUBLE_ENCODED_CHARACTER.matcher(string);
		while (matcher.find()) {
			if (isClearlyDoubleEncoded(matcher.group()) && decode(matcher.group()).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isClearlyDoubleEncoded(final String sequence) {
		final var lead = sequence.charAt(0);
		return lead == '\u00C2' || lead == '\u00C3' || sequence.chars().skip(1).anyMatch(character -> character <= '\u009F');
	}

	/**
	 * Decodes the characters, taken as ISO-8859-1 bytes, as UTF-8, if they are valid UTF-8.
	 */
	private static Optional<String> decode(final String characters) {
		try {
			return Optional.of(UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(characters.getBytes(ISO_8859_1)))
				.toString());
		} catch (final CharacterCodingException _) {
			return Optional.empty();
		}
	}
}
