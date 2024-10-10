package se.sundsvall.dept44.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EncodingUtilsTest {

	@ParameterizedTest
	@MethodSource("isDoubleEncodedUTF8ContentArguments")
	void isDoubleEncodedUTF8Content(String stringToCheck, boolean isDoubleEncoded) {
		assertThat(EncodingUtils.isDoubleEncodedUTF8Content(stringToCheck)).isEqualTo(isDoubleEncoded);
	}

	@ParameterizedTest
	@MethodSource("fixDoubleEncodedUTF8ContentArguments")
	void fixDoubleEncodedUTF8Content(String doubleEncodedString, String fixedString) {
		assertThat(EncodingUtils.fixDoubleEncodedUTF8Content(doubleEncodedString))
				.isEqualTo(fixedString);
	}

	private static Stream<Arguments> isDoubleEncodedUTF8ContentArguments() {
		return Stream.of(
				Arguments.of("AnvÃ¤ndarhantering", true),
				Arguments.of("Ãndring", true),
				Arguments.of("AvbestÃ¤llning", true),
				Arguments.of("FrÃ¥gor & Information frÃ¥n anvÃ¤ndare & kunder", true),
				Arguments.of("LÃ¶pande underhÃ¥ll", true),
				Arguments.of("Uppdatering/fÃ¶rÃ¤ndring", true),
				Arguments.of("VÃ¤xelfÃ¶rÃ¤ndring", true),
				Arguments.of("ÃÃÃÃ¥Ã¤Ã¶", true),
				Arguments.of("Användarhantering", false),
				Arguments.of("Ändring", false),
				Arguments.of("Avbeställning", false),
				Arguments.of("Frågor & Information från användare & kunder", false),
				Arguments.of("Löpande underhåll", false),
				Arguments.of("Uppdatering/förändring", false),
				Arguments.of("Växelförändring", false),
				Arguments.of("ÅÄÖåäö", false));
	}

	private static Stream<Arguments> fixDoubleEncodedUTF8ContentArguments() {
		return Stream.of(
				Arguments.of("AnvÃ¤ndarhantering", "Användarhantering"),
				Arguments.of("Ãndring", "Ändring"),
				Arguments.of("AvbestÃ¤llning", "Avbeställning"),
				Arguments.of(
						"FrÃ¥gor & Information frÃ¥n anvÃ¤ndare & kunder",
						"Frågor & Information från användare & kunder"),
				Arguments.of("LÃ¶pande underhÃ¥ll", "Löpande underhåll"),
				Arguments.of("Uppdatering/fÃ¶rÃ¤ndring", "Uppdatering/förändring"),
				Arguments.of("VÃ¤xelfÃ¶rÃ¤ndring", "Växelförändring"),
				Arguments.of("ÃÃÃÃ¥Ã¤Ã¶", "ÅÄÖåäö"));
	}
}
