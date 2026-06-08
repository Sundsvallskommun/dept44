package se.sundsvall.dept44.util;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

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
			// A 14-digit run is not a personnummer; the \b boundaries keep it from matching.
			Arguments.of("Order 12345678901234 shipped", "Order 12345678901234 shipped"),
			Arguments.of("no digits here", "no digits here"),
			Arguments.of("900101-1234 and 850615-4321", "******-**** and ******-****"));
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
			Arguments.of("no phone here", "no phone here"));
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

	private static Stream<Arguments> maskPiiArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("", ""),
			Arguments.of("nothing to mask", "nothing to mask"),
			Arguments.of(
				"User john.doe@example.com phone 070-123 45 67 partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234",
				"User j***@example.com phone ***-*** ** ** partyId f47a... ssn ******-****"),
			// UUID is masked first, so its twelve hexadecimal digits cannot be mistaken for a personal number.
			Arguments.of("id 12345678-1234-1234-1234-123456789012", "id 1234..."));
	}
}
