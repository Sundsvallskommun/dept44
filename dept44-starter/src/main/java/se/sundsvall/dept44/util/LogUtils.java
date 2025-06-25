package se.sundsvall.dept44.util;

import java.util.Optional;

public final class LogUtils {

	private LogUtils() {}

	/**
	 * Sanitizes a string to make it safe for logging by removing or replacing potentially harmful characters.
	 * The purpose is to prevent Log injection: https://owasp.org/www-community/attacks/Log_Injection
	 *
	 * <p>
	 * This method performs the following transformations on the input string:
	 * <ul>
	 * <li>Replaces newline ({@code \n}) and carriage return ({@code \r}) characters with spaces.</li>
	 * <li>Removes all non-printable ASCII characters (characters outside the range 0x20 to 0x7E).</li>
	 * <li>Removes percent signs ({@code %}) and backslashes ({@code \}) to prevent log injection or formatting issues.</li>
	 * </ul>
	 * 
	 * If the input is {@code null}, the method returns {@code null}.
	 *
	 * @param  input the string to sanitize
	 * @return       a sanitized version of the input string suitable for logging, or {@code null} if the input was
	 *               {@code null}
	 */
	public static String sanitizeForLogging(String input) {
		return Optional.ofNullable(input)
			.map(string -> string
				// Replace all newlines and carriage returns with spaces.
				.replaceAll("[\\r\\n]", " ")
				// Remove all non-printable ASCII characters.
				.replaceAll("[^\\x20-\\x7E]", "")
				// Escape or remove other potentially harmful characters.
				.replaceAll("[%\\\\]", ""))
			.orElse(null);
	}
}
