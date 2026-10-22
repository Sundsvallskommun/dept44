package se.sundsvall.dept44.util;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.dept44.util.PiiMasker.Category;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PiiMaskerTest {

	@ParameterizedTest
	@MethodSource("maskPersonalNumberArguments")
	void maskPersonalNumber(final String input, final String expected) {
		assertThat(PiiMasker.maskPersonalNumber(input)).isEqualTo(expected);
	}

	private static Stream<Arguments> maskPersonalNumberArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("900101-1234", "******-****"),
			Arguments.of("9001011234", "******-****"),
			Arguments.of("900101+1234", "******-****"),
			Arguments.of("Patient 900101-1234 admitted", "Patient ******-**** admitted"),
			// Twelve-digit (full-century) personnummer / legalId - the form the Party service returns.
			Arguments.of("199001011234", "******-****"),
			Arguments.of("19900101-1234", "******-****"),
			Arguments.of("19900101+1234", "******-****"),
			Arguments.of("legalId 199001011234 fetched", "legalId ******-**** fetched"),
			// A 13-digit run (e.g. an epoch-millisecond timestamp) is not a personnummer; the \b boundaries keep it from
			// matching.
			Arguments.of("At 1700000000000 the job ran", "At 1700000000000 the job ran"),
			// A 14-digit run is not a personnummer; the \b boundaries keep it from matching.
			Arguments.of("Order 12345678901234 shipped", "Order 12345678901234 shipped"),
			Arguments.of("no digits here", "no digits here"),
			Arguments.of("900101-1234 and 850615-4321", "******-**** and ******-****"),
			Arguments.of("199001011234 and 850615-4321", "******-**** and ******-****"),
			Arguments.of("beslut_199001011234.pdf", "beslut_******-****.pdf"),
			Arguments.of("199001011234_underlag.pdf", "******-****_underlag.pdf"),
			Arguments.of("hash 3fa85f6457174562b3fc", "hash 3fa85f6457174562b3fc"));
	}

	@ParameterizedTest
	@MethodSource("maskPhoneNumberArguments")
	void maskPhoneNumber(final String input, final String expected) {
		assertThat(PiiMasker.maskPhoneNumber(input)).isEqualTo(expected);
	}

	private static Stream<Arguments> maskPhoneNumberArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("070-123 45 67", "***-*** ** **"),
			Arguments.of("+46 70 123 45 67", "+** ** *** ** **"),
			Arguments.of("0046 70 123 45 67", "**** ** *** ** **"),
			Arguments.of("+46701234567", "+***********"),
			Arguments.of("08-123 456 78", "**-*** *** **"),
			Arguments.of("Call 060-12 34 56 today", "Call ***-** ** ** today"),
			// A bare digit run has no phone structure; it is left to the personal-number rule, so it is unchanged here.
			Arguments.of("0701234567", "0701234567"),
			Arguments.of("no phone here", "no phone here"),
			Arguments.of("Call 060-12 34 56.", "Call ***-** ** **."),
			Arguments.of("2024-03-08 070-123 45 67", "2024-03-08 ***-*** ** **"),
			Arguments.of("Job scheduled at 2024-03-08 09:15:22", "Job scheduled at 2024-03-08 09:15:22"),
			Arguments.of("2025-01-15 12:30:00.0", "2025-01-15 12:30:00.0"),
			Arguments.of("Period 2024-05-01 2024-06-01", "Period 2024-05-01 2024-06-01"),
			Arguments.of("08.05.2024 07:30", "08.05.2024 07:30"),
			Arguments.of("2024-03-08T09:15:22.123 done", "2024-03-08T09:15:22.123 done"),
			// A phone number right after a digit and a slash or hyphen, or before a decimal point, is still masked whole
			Arguments.of("Tel 060-123 45/070-123 45 67", "Tel ***-*** **/***-*** ** **"),
			Arguments.of("08-123 45 67/+46 70 123 45 67", "**-*** ** **/+** ** *** ** **"),
			Arguments.of("070-1234567/070-7654321", "***-*******/***-*******"),
			Arguments.of("GET /2281/070-1234567", "GET /2281/***-*******"),
			Arguments.of("070-123 45 67.5", "***-*** ** **.5"),
			// A phone number that starts like a date is not taken for one
			Arguments.of("0701-23-45-67", "****-**-**-**"),
			Arguments.of("0046-70-1234567", "****-**-*******"),
			Arguments.of("0046-70-123 45 67", "****-**-*** ** **"));
	}

	@Test
	void maskPhoneNumberScansALongValueInOnePass() {
		// About 500 KB with 10 000 timestamps, as in a large payload log line: scanned again for every match, this took minutes
		final var value = IntStream.range(0, 10_000)
			.mapToObj(index -> "{\"created\":\"2024-03-08T09:15:22.123\",\"id\":" + index + "}")
			.collect(joining(",", "[", "]"));

		final var masked = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> PiiMasker.maskPhoneNumber(value));

		assertThat(masked).isEqualTo(value);
	}

	@ParameterizedTest
	@MethodSource("maskUuidArguments")
	void maskUuid(final String input, final String expected) {
		assertThat(PiiMasker.maskUuid(input)).isEqualTo(expected);
	}

	private static Stream<Arguments> maskUuidArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("f47ac10b-58cc-4372-a567-0e02b2c3d479", "f47a..."),
			Arguments.of("F47AC10B-58CC-4372-A567-0E02B2C3D479", "F47A..."),
			Arguments.of("partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 done", "partyId f47a... done"),
			Arguments.of("not-a-uuid", "not-a-uuid"),
			Arguments.of("f47ac10b-58cc-4372-a567-0e02b2c3d479 / 11111111-2222-3333-4444-555555555555", "f47a... / 1111..."));
	}

	@ParameterizedTest
	@MethodSource("maskEmailArguments")
	void maskEmail(final String input, final String expected) {
		assertThat(PiiMasker.maskEmail(input)).isEqualTo(expected);
	}

	private static Stream<Arguments> maskEmailArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("john.doe@example.com", "j***@example.com"),
			Arguments.of("a@b.co", "a***@b.co"),
			Arguments.of("Contact john.doe@example.com today", "Contact j***@example.com today"),
			Arguments.of("no email here", "no email here"),
			Arguments.of("a@b.com, c@d.org", "a***@b.com, c***@d.org"));
	}

	@ParameterizedTest
	@MethodSource("maskPiiArguments")
	void maskPii(final String input, final String expected) {
		assertThat(PiiMasker.maskPii(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("maskPiiArguments")
	void maskPiiMasksEveryCategoryWhenEveryCategoryIsNamed(final String input, final String expected) {
		assertThat(PiiMasker.maskPii(input, EnumSet.allOf(Category.class))).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("maskNamedCategoriesArguments")
	void maskPiiMasksOnlyTheCategoriesNamed(final Set<Category> categories, final String input, final String expected) {
		assertThat(PiiMasker.maskPii(input, categories)).isEqualTo(expected);
	}

	private static Stream<Arguments> maskNamedCategoriesArguments() {
		final var value = "User john.doe@example.com phone 070-123 45 67 errandId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234";
		return Stream.of(
			// The reason the categories are separable at all: an incident is traced by the identifiers, and masking
			// leaves four characters of one
			Arguments.of(EnumSet.complementOf(EnumSet.of(Category.UUID)), value,
				"User j***@example.com phone ***-*** ** ** errandId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn ******-****"),
			Arguments.of(EnumSet.of(Category.UUID), value,
				"User john.doe@example.com phone 070-123 45 67 errandId f47a... ssn 900101-1234"),
			Arguments.of(EnumSet.of(Category.PERSONAL_NUMBER), value,
				"User john.doe@example.com phone 070-123 45 67 errandId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn ******-****"),
			Arguments.of(EnumSet.of(Category.EMAIL), value,
				"User j***@example.com phone 070-123 45 67 errandId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234"),
			Arguments.of(EnumSet.of(Category.PHONE_NUMBER), value,
				"User john.doe@example.com phone ***-*** ** ** errandId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234"),
			// Nothing to mask with, so nothing is masked
			Arguments.of(EnumSet.noneOf(Category.class), value, value),
			Arguments.of(null, value, value),
			Arguments.of(EnumSet.allOf(Category.class), null, null));
	}

	@ParameterizedTest
	@MethodSource("uuidKeptWholeArguments")
	void maskPiiLeavesAKeptUuidWholeEvenWhereItsDigitsReadAsAPersonalNumber(final String input, final String expected) {
		// The first two groups of such a UUID are a ten-digit number as far as the personal-number pattern is
		// concerned, so a UUID that is not masked has to be stepped over rather than scanned
		assertThat(PiiMasker.maskPii(input, EnumSet.complementOf(EnumSet.of(Category.UUID)))).isEqualTo(expected);
	}

	private static Stream<Arguments> uuidKeptWholeArguments() {
		return Stream.of(
			Arguments.of("id 12345678-1234-1234-1234-123456789012", "id 12345678-1234-1234-1234-123456789012"),
			Arguments.of("id 00112233-4455-6677-8899-aabbccddeeff", "id 00112233-4455-6677-8899-aabbccddeeff"),
			Arguments.of("id f47ac10b-58cc-4372-a567-0e02b2c3d479", "id f47ac10b-58cc-4372-a567-0e02b2c3d479"),
			// The text on either side of a kept UUID is still masked, as is a personal number between two of them
			Arguments.of("12345678-1234-1234-1234-123456789012 ssn 900101-1234 00112233-4455-6677-8899-aabbccddeeff",
				"12345678-1234-1234-1234-123456789012 ssn ******-**** 00112233-4455-6677-8899-aabbccddeeff"),
			Arguments.of("ssn 199001011234 id 12345678-1234-1234-1234-123456789012 mail a@b.co",
				"ssn ******-**** id 12345678-1234-1234-1234-123456789012 mail a***@b.co"));
	}

	@ParameterizedTest
	@MethodSource("emailCarryingAUuidArguments")
	void maskPiiMasksAnEmailAddressCarryingAKeptUuidAsOneAddress(final String input, final String expected) {
		// Stepping over the UUID would cut such an address in two, and neither half reads as an e-mail address
		assertThat(PiiMasker.maskPii(input, EnumSet.complementOf(EnumSet.of(Category.UUID)))).isEqualTo(expected);
	}

	private static Stream<Arguments> emailCarryingAUuidArguments() {
		return Stream.of(
			Arguments.of("to john.doe+f47ac10b-58cc-4372-a567-0e02b2c3d479@example.com", "to j***@example.com"),
			Arguments.of("to anna.svensson.f47ac10b-58cc-4372-a567-0e02b2c3d479@example.com", "to a***@example.com"),
			Arguments.of("to f47ac10b-58cc-4372-a567-0e02b2c3d479@reply.sundsvall.se", "to f***@reply.sundsvall.se"),
			// The domain is kept whole when masking, and so is a UUID in it
			Arguments.of("to john@f47ac10b-58cc-4372-a567-0e02b2c3d479.example.com",
				"to j***@f47ac10b-58cc-4372-a567-0e02b2c3d479.example.com"));
	}

	@Test
	void maskPiiStaysLinearWithManyKeptUuids() {
		// A kept UUID splits the value, and each part is masked on its own: that must stay linear in the length
		final var value = IntStream.range(0, 10_000)
			.mapToObj(index -> "{\"id\":\"12345678-1234-1234-1234-12345678901" + (index % 10) + "\",\"ssn\":\"900101-1234\"}")
			.collect(joining(",", "[", "]"));

		final var masked = assertTimeoutPreemptively(Duration.ofSeconds(5),
			() -> PiiMasker.maskPii(value, EnumSet.complementOf(EnumSet.of(Category.UUID))));

		assertThat(masked)
			.doesNotContain("900101-1234")
			.contains("12345678-1234-1234-1234-123456789010");
	}

	private static Stream<Arguments> maskPiiArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("nothing to mask", "nothing to mask"),
			Arguments.of(
				"User john.doe@example.com phone 070-123 45 67 partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234",
				"User j***@example.com phone ***-*** ** ** partyId f47a... ssn ******-****"),
			// UUID is masked first, so its twelve hexadecimal digits cannot be mistaken for a personal number.
			Arguments.of("id 12345678-1234-1234-1234-123456789012", "id 1234..."),
			// Twelve-digit legalId is masked by the personal-number rule.
			Arguments.of("legalId 199001011234 partyId f47ac10b-58cc-4372-a567-0e02b2c3d479", "legalId ******-**** partyId f47a..."));
	}
}
