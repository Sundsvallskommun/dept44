package se.sundsvall.dept44.util;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class EncodingUtilsTest {

	private static Stream<Arguments> isDoubleEncodedUTF8ContentArguments() {
		return Stream.of(
			Arguments.of("AnvÃ¤ndarhantering", true),
			Arguments.of("Ãndring", true),
			Arguments.of("AvbestÃ¤llning", true),
			Arguments.of("FrÃ¥gor & Information frÃ¥n anvÃ¤ndare & kunder", true),
			Arguments.of("LÃ¶pande underhÃ¥ll", true),
			Arguments.of("Uppdatering/fÃ¶rÃ¤ndring", true),
			Arguments.of("VÃ¤xelfÃ¶rÃ¤ndring", true),
			// spotless:off
			Arguments.of("ÃÃÃÃ¥Ã¤Ã¶", true),
			//spotless:on
			Arguments.of("Användarhantering", false),
			Arguments.of("Ändring", false),
			Arguments.of("Avbeställning", false),
			Arguments.of("Frågor & Information från användare & kunder", false),
			Arguments.of("Löpande underhåll", false),
			Arguments.of("Uppdatering/förändring", false),
			Arguments.of("Växelförändring", false),
			Arguments.of("ÅÄÖåäö", false),
			Arguments.of("«яблоку»", false),
			Arguments.of("Сказку\u00a0читай", false),
			Arguments.of("Ålder: 3 år; \u00c3\u0085sa \u00c3\u0096berg", true),
			Arguments.of("plain ascii", false),
			// Correctly encoded text whose characters happen to form valid UTF-8 byte sequences
			Arguments.of("TVÅ\u00a0BARN", false),
			Arguments.of("Ö»", false),
			Arguments.of("på\u00a0\u00a0och", false),
			Arguments.of(null, false));
	}

	private static Stream<Arguments> fixDoubleEncodedUTF8ContentArguments() {
		return Stream.of(
			Arguments.of("AnvÃ¤ndarhantering", "Användarhantering"),
			Arguments.of("Ãndring", "Ändring"),
			Arguments.of("AvbestÃ¤llning", "Avbeställning"),
			Arguments.of("FrÃ¥gor & Information frÃ¥n anvÃ¤ndare & kunder", "Frågor & Information från användare & kunder"),
			Arguments.of("LÃ¶pande underhÃ¥ll", "Löpande underhåll"),
			Arguments.of("Uppdatering/fÃ¶rÃ¤ndring", "Uppdatering/förändring"),
			Arguments.of("VÃ¤xelfÃ¶rÃ¤ndring", "Växelförändring"),
			Arguments.of("«яблоку»", "«яблоку»"),
			Arguments.of("Ålder: 3 år; Ã\u0085sa Ã\u0096berg", "Ålder: 3 år; Åsa Öberg"),
			Arguments.of("€: â\u0082¬, emoji: ð\u009f\u0098\u0080", "€: €, emoji: 😀"),
			Arguments.of("Сказку читай", "Сказку читай"),
			Arguments.of("TVÅ\u00a0BARN", "TVÅ\u00a0BARN"),
			Arguments.of("Ö»", "Ö»"),
			Arguments.of("på\u00a0\u00a0och", "på\u00a0\u00a0och"),
			Arguments.of(null, null),
			// spotless:off
			Arguments.of("ÃÃÃÃ¥Ã¤Ã¶", "ÅÄÖåäö"));
	}//spotless:on

	@ParameterizedTest
	@MethodSource("isDoubleEncodedUTF8ContentArguments")
	void isDoubleEncodedUTF8Content(final String stringToCheck, final boolean isDoubleEncoded) {
		assertThat(EncodingUtils.isDoubleEncodedUTF8Content(stringToCheck)).isEqualTo(isDoubleEncoded);
	}

	@ParameterizedTest
	@MethodSource("fixDoubleEncodedUTF8ContentArguments")
	void fixDoubleEncodedUTF8Content(final String doubleEncodedString, final String fixedString) {
		assertThat(EncodingUtils.fixDoubleEncodedUTF8Content(doubleEncodedString)).isEqualTo(fixedString);
	}

}
