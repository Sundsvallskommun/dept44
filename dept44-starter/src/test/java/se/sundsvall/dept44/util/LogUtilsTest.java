package se.sundsvall.dept44.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LogUtilsTest {

	private static final char ESCAPE = 0x1B;
	private static final char ACKNOWLEDGE = 0x6;
	private static final char BACKSPACE = 0x8;

	@ParameterizedTest
	@MethodSource("sanitizeForLoggingArguments")
	void sanitizeForLogging(final String stringToSanitize, final String sanitizedString) {
		assertThat(LogUtils.sanitizeForLogging(stringToSanitize)).isEqualTo(sanitizedString);
	}

	private static Stream<Arguments> sanitizeForLoggingArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of("The \n\n\n string", "The     string"),
			Arguments.of("The \r\r\r string", "The     string"),
			Arguments.of("The \r\n\r\n\r\n string", "The        string"),
			Arguments.of("The string" + BACKSPACE, "The string"),
			Arguments.of("The string" + ACKNOWLEDGE, "The string"),
			Arguments.of("The string" + ESCAPE, "The string"),
			Arguments.of("The %string%", "The string"),
			Arguments.of("The \bstring\\", "The string"));
	}
}
