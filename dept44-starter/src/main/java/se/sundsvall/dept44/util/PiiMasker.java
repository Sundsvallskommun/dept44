package se.sundsvall.dept44.util;

import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personally identifiable information (PII) in strings so it can be logged without exposing personal data.
 *
 * <p>
 * This class handles the PII categories most commonly leaked into logs in Sundsvall services:
 * <ul>
 * <li>Swedish personal identity numbers ({@code personnummer}) &rarr; {@code ******-****}</li>
 * <li>UUIDs (typically a {@code partyId}) &rarr; the first four characters followed by {@code ...}</li>
 * <li>E-mail addresses &rarr; the first character of the local part followed by {@code ***@} and the domain</li>
 * <li>Structured Swedish phone numbers &rarr; every digit replaced with {@code *}, separators kept</li>
 * </ul>
 *
 * <p>
 * <strong>This is not the same thing as {@link LogUtils#sanitizeForLogging(String)}.</strong>
 * {@code sanitizeForLogging}
 * protects against <em>log injection</em> (CRLF and control characters that let an attacker forge log lines); it does
 * <em>not</em> hide personal data. The methods in this class do the opposite: they mask personal data but do
 * <em>not</em> protect against log injection. The two are complementary - apply both when a value is both attacker
 * controlled and contains PII.
 *
 * <p>
 * <strong>Limitation:</strong> the personal-identity-number matcher keys on the digit shape of a ten-digit
 * ({@code NNNNNN[-+]?NNNN}) or twelve-digit ({@code NNNNNNNN[-+]?NNNN}) number and cannot distinguish a real
 * personnummer from any other free-standing ten- or twelve-digit number (an order number, an epoch-millisecond
 * timestamp, a correlation id). Such numbers may be masked even though they are not PII. Eliminating this would require
 * a date/checksum validation that is intentionally out of scope here.
 *
 * <p>
 * <strong>Not handled:</strong> free-form street addresses are deliberately not masked - they have no stable shape a
 * regex can match without masking large amounts of ordinary log text. Mask addresses field-by-field at the source
 * instead (or via the Logbook JSONPath/XPath body masking for HTTP payloads). The phone-number matcher only catches
 * structured Swedish numbers (a {@code +46}/{@code 0046} prefix or an internal separator); a bare run of digits such as
 * {@code 0701234567} is treated as a personal identity number, not a phone number.
 *
 * @see LogUtils#sanitizeForLogging(String)
 */
public final class PiiMasker {

	/**
	 * Swedish personal identity number on the ten-digit {@code NNNNNN[-+]?NNNN} or twelve-digit
	 * {@code NNNNNNNN[-+]?NNNN} form (the optional {@code \d{2}} is the two-digit century prefix). The number must not
	 * be preceded or followed by a letter or digit, which keeps a run that is part of a longer number (or a token such as
	 * a hash) from matching, while still matching next to {@code _} as in {@code beslut_199001011234.pdf}.
	 */
	private static final Pattern PERSONAL_NUMBER_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d{6}(?:\\d{2})?[-+]?\\d{4}(?![A-Za-z0-9])");

	private static final String PERSONAL_NUMBER_MASK = "******-****";

	/**
	 * Structured Swedish phone number: either a {@code +46}/{@code 0046} country-code prefix, or a national number with a
	 * leading {@code 0} and at least one space/hyphen separator. Requiring that structure keeps a bare run of digits from
	 * matching (such a run is handled by {@link #maskPersonalNumber(String)} instead).
	 * <p>
	 * Dates and times, such as {@code 2024-03-08 09:15:22} or {@code 08.05.2024 07:30}, are found as well and left as
	 * they are, so that no part of them is taken for a phone number. A date must not be next to more digits, so that a
	 * phone number such as {@code 0701-23-45-67} is not taken for one. All patterns are tried as the alternatives of a
	 * single pattern would be: what starts first is taken, and when two match at the same place the earlier pattern wins.
	 * They are kept apart, rather than joined into one pattern, to keep each of them simple.
	 */
	private static final List<Pattern> PHONE_NUMBER_PATTERNS = List.of(
		Pattern.compile("(?<!\\d)\\d{4}[-/.]\\d{2}[-/.]\\d{2}(?:[ T]\\d{2}:\\d{2}(?::\\d{2})?)?(?:[.,]\\d+)?(?![-/.]?\\d)"),
		Pattern.compile("(?<!\\d)\\d{2}[-/.]\\d{2}[-/.]\\d{4}(?: \\d{2}:\\d{2}(?::\\d{2})?)?(?![-/.]?\\d)"),
		phoneNumberPattern("(?:\\+46|0046)[\\s-]?\\d(?:[\\s-]?\\d){6,10}"),
		phoneNumberPattern("0\\d{1,3}[\\s-]\\d{2,4}(?:[\\s-]?\\d{2,3}){1,2}"));

	/** The number of patterns at the start of {@link #PHONE_NUMBER_PATTERNS} that find dates and times. */
	private static final int DATE_TIME_PATTERNS = 2;

	/** UUID in the canonical {@code 8-4-4-4-12} hexadecimal form, e.g. a {@code partyId}. */
	private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

	/** E-mail address. Group 1 captures the local part, group 2 captures the domain. */
	private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

	private PiiMasker() {}

	/**
	 * Masks every supported PII category in the given string in one pass.
	 *
	 * <p>
	 * The categories are applied in a deliberate order - UUID, then phone number, then personal identity number, then
	 * e-mail - so that one mask cannot corrupt another's match (e.g. the personal-number matcher biting into the twelve
	 * hexadecimal digits of a UUID or the digits of a structured phone number, or a masked value being mistaken for an
	 * e-mail).
	 *
	 * <p>
	 * This masks personal data only; it does <em>not</em> protect against log injection. For that, use
	 * {@link LogUtils#sanitizeForLogging(String)}.
	 *
	 * @param  input the string to mask
	 * @return       a copy with all supported PII masked, or {@code null} if the input was {@code null}
	 * @see          LogUtils#sanitizeForLogging(String)
	 */
	public static String maskPii(final String input) {
		return Optional.ofNullable(input)
			.map(PiiMasker::maskUuid)
			.map(PiiMasker::maskPhoneNumber)
			.map(PiiMasker::maskPersonalNumber)
			.map(PiiMasker::maskEmail)
			.orElse(null);
	}

	/**
	 * Masks Swedish personal identity numbers on the ten-digit {@code NNNNNN[-+]?NNNN} or twelve-digit
	 * {@code NNNNNNNN[-+]?NNNN} form, replacing each with {@code ******-****}.
	 *
	 * <p>
	 * Note the limitation described on the {@linkplain PiiMasker class}: any free-standing ten- or twelve-digit number
	 * matches this shape and will be masked, whether or not it is an actual personnummer.
	 *
	 * @param  input the string to mask
	 * @return       a copy with personal identity numbers masked, or {@code null} if the input was {@code null}
	 */
	public static String maskPersonalNumber(final String input) {
		return Optional.ofNullable(input)
			.map(value -> PERSONAL_NUMBER_PATTERN.matcher(value).replaceAll(PERSONAL_NUMBER_MASK))
			.orElse(null);
	}

	/**
	 * Masks structured Swedish phone numbers by replacing every digit with {@code *} while keeping separators, e.g.
	 * {@code 070-123 45 67} becomes {@code ***-*** ** **} and {@code +46 70 123 45 67} becomes {@code +** ** *** ** **}.
	 *
	 * <p>
	 * Only numbers with phone-like structure are matched - a {@code +46}/{@code 0046} prefix or an internal separator. A
	 * bare run of digits is left to {@link #maskPersonalNumber(String)}.
	 *
	 * @param  input the string to mask
	 * @return       a copy with structured phone numbers masked, or {@code null} if the input was {@code null}
	 */
	public static String maskPhoneNumber(final String input) {
		return Optional.ofNullable(input)
			.map(PiiMasker::maskPhoneNumberDigits)
			.orElse(null);
	}

	private static String maskPhoneNumberDigits(final String value) {
		final var masked = new StringBuilder(value);
		final var scanner = new PhoneNumberScanner(value);
		for (var index = scanner.nextAt(0); index >= 0; index = scanner.nextAt(scanner.end(index))) {
			if (index >= DATE_TIME_PATTERNS) {
				maskDigits(masked, scanner.start(index), scanner.end(index));
			}
		}
		return masked.toString();
	}

	/**
	 * Finds the phone numbers, dates and times in a value in one pass. Each pattern keeps its next match, and is only
	 * searched again once a match has been taken from where that one starts, so that a long value with many matches is
	 * not scanned again for every match.
	 */
	private static final class PhoneNumberScanner {

		private final List<Matcher> matchers;
		private final MatchResult[] nextMatches;

		private PhoneNumberScanner(final String value) {
			this.matchers = PHONE_NUMBER_PATTERNS.stream().map(pattern -> pattern.matcher(value)).toList();
			this.nextMatches = new MatchResult[matchers.size()];
			for (var index = 0; index < matchers.size(); index++) {
				nextMatches[index] = find(matchers.get(index), 0);
			}
		}

		/**
		 * The index of the pattern whose match starts first at or after the given position, or -1 when there is none. Of
		 * two that start at the same position, the earlier pattern wins.
		 */
		private int nextAt(final int from) {
			var first = -1;
			for (var index = 0; index < nextMatches.length; index++) {
				if (nextMatches[index] != null && nextMatches[index].start() < from) {
					nextMatches[index] = find(matchers.get(index), from);
				}
				if (nextMatches[index] != null && (first < 0 || nextMatches[index].start() < nextMatches[first].start())) {
					first = index;
				}
			}
			return first;
		}

		private int start(final int index) {
			return nextMatches[index].start();
		}

		private int end(final int index) {
			return nextMatches[index].end();
		}

		private static MatchResult find(final Matcher matcher, final int from) {
			return matcher.find(from) ? matcher.toMatchResult() : null;
		}
	}

	private static void maskDigits(final StringBuilder text, final int start, final int end) {
		for (var index = start; index < end; index++) {
			if (isAsciiDigit(text.charAt(index))) {
				text.setCharAt(index, '*');
			}
		}
	}

	private static boolean isAsciiDigit(final char character) {
		return character >= '0' && character <= '9';
	}

	/**
	 * A phone number in the given form that does not start right after a word character and does not end before a digit.
	 */
	private static Pattern phoneNumberPattern(final String number) {
		return Pattern.compile("(?<!\\w)" + number + "(?!\\d)");
	}

	/**
	 * Masks UUIDs (such as a {@code partyId}) by keeping only the first four characters and appending {@code ...}, e.g.
	 * {@code f47ac10b-58cc-4372-a567-0e02b2c3d479} becomes {@code f47a...}.
	 *
	 * @param  input the string to mask
	 * @return       a copy with UUIDs masked, or {@code null} if the input was {@code null}
	 */
	public static String maskUuid(final String input) {
		return Optional.ofNullable(input)
			.map(value -> UUID_PATTERN.matcher(value).replaceAll(match -> match.group().substring(0, 4) + "..."))
			.orElse(null);
	}

	/**
	 * Masks e-mail addresses by keeping the first character of the local part and the domain, replacing the rest of the
	 * local part with {@code ***}, e.g. {@code john.doe@example.com} becomes {@code j***@example.com}.
	 *
	 * @param  input the string to mask
	 * @return       a copy with e-mail addresses masked, or {@code null} if the input was {@code null}
	 */
	public static String maskEmail(final String input) {
		return Optional.ofNullable(input)
			.map(value -> EMAIL_PATTERN.matcher(value).replaceAll(match -> match.group(1).substring(0, 1) + "***@" + match.group(2)))
			.orElse(null);
	}
}
