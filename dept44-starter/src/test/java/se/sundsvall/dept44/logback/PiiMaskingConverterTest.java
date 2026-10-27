package se.sundsvall.dept44.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.dept44.util.PiiMasker;
import se.sundsvall.dept44.util.PiiMasker.Category;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingConverterTest {

	private static final String MESSAGE = "User john.doe@example.com partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234";
	private static final String MASKED = "User j***@example.com partyId f47a... ssn ******-****";

	private final LoggerContext context = new LoggerContext();

	@ParameterizedTest
	@ValueSource(strings = {
		"true", "TRUE", " true "
	})
	void masksWhenEnabled(final String value) {
		assertThat(startedConverter(value).convert(event(MESSAGE))).isEqualTo(MASKED);
	}

	@Test
	void returnsMessageVerbatimWhenDisabled() {
		assertThat(startedConverter("false").convert(event(MESSAGE))).isEqualTo(MESSAGE);
	}

	@Test
	void returnsMessageVerbatimWhenPropertyAbsent() {
		assertThat(startedConverter(null).convert(event(MESSAGE))).isEqualTo(MESSAGE);
	}

	@Test
	void masksEveryCategoryWhenNoCategoryIsTurnedOff() {
		// A service that says nothing about the categories, and one whose own logback-spring.xml does not carry the
		// properties, masks everything as before
		assertThat(startedConverter("true").convert(event(MESSAGE))).isEqualTo(MASKED);
	}

	@Test
	void keepsUuidsWholeWhenThatCategoryIsTurnedOff() {
		context.putProperty(PiiMaskingConverter.propertyOf(Category.UUID), "false");

		assertThat(startedConverter("true").convert(event(MESSAGE)))
			.isEqualTo("User j***@example.com partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn ******-****");
	}

	@ParameterizedTest
	@EnumSource(Category.class)
	void turnsOffOneCategoryAtATime(final Category turnedOff) {
		context.putProperty(PiiMaskingConverter.propertyOf(turnedOff), "false");
		final var expected = PiiMasker.maskPii(MESSAGE, EnumSet.complementOf(EnumSet.of(turnedOff)));

		assertThat(startedConverter("true").convert(event(MESSAGE))).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"true", "TRUE", "yes", "", " ", "nonsense"
	})
	void masksACategoryUnlessItIsExplicitlyTurnedOff(final String value) {
		context.putProperty(PiiMaskingConverter.propertyOf(Category.UUID), value);

		assertThat(startedConverter("true").convert(event(MESSAGE))).isEqualTo(MASKED);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"false", "FALSE", " false "
	})
	void readsTurnedOffWhateverItsCase(final String value) {
		context.putProperty(PiiMaskingConverter.propertyOf(Category.UUID), value);

		assertThat(startedConverter("true").convert(event(MESSAGE))).contains("f47ac10b-58cc-4372-a567-0e02b2c3d479");
	}

	@Test
	void masksNothingWhenEveryCategoryIsTurnedOff() {
		for (final var category : Category.values()) {
			context.putProperty(PiiMaskingConverter.propertyOf(category), "false");
		}

		assertThat(startedConverter("true").convert(event(MESSAGE))).isEqualTo(MESSAGE);
	}

	@Test
	void leavesTheMessageAloneWhenMaskingIsOffWhateverTheCategoriesSay() {
		context.putProperty(PiiMaskingConverter.propertyOf(Category.UUID), "true");

		assertThat(startedConverter("false").convert(event(MESSAGE))).isEqualTo(MESSAGE);
	}

	@Test
	void resolvesConversionWordThroughPatternLayout() {
		context.putProperty(PiiMaskingConverter.ENABLED_PROPERTY, "true");

		final var layout = new PatternLayout();
		layout.setContext(context);
		layout.getInstanceConverterMap().put("maskPii", PiiMaskingConverter::new);
		layout.setPattern("%maskPii");
		layout.start();

		assertThat(layout.doLayout(event(MESSAGE))).isEqualTo(MASKED);
	}

	/**
	 * Values are placed across the limit and across the end of every window the converter may mask, after text that
	 * masking leaves alone, shortens or lengthens, and after a value cut in two. The step is a prime that divides the
	 * length of no filler, so that a value lands in a different place of the filler each time.
	 */
	@ParameterizedTest
	@ValueSource(booleans = {
		true, false
	})
	void returnsTheBeginningOfTheWholeMessageMaskedWithALimit(final boolean uuidsMasked) {
		context.putProperty(PiiMaskingConverter.propertyOf(Category.UUID), String.valueOf(uuidsMasked));
		final var categories = uuidsMasked ? EnumSet.allOf(Category.class) : EnumSet.complementOf(EnumSet.of(Category.UUID));
		final var converter = startedConverter("true", "100");
		final var fillers = List.of("ordinary words ", "f47ac10b-58cc-4372-a567-0e02b2c3d479 ", "a@b.se ", "a".repeat(64) + "@b.se ");
		final var values = List.of("anna.andersson@sundsvall.se", "199001011234", "900101-1234", "070-123 45 67", "+46 70 123 45 67",
			"f47ac10b-58cc-4372-a567-0e02b2c3d479", "2024-03-08 09:15:22", "x".repeat(1500) + "@example.com", "0701-23-45." + "1".repeat(1500) + "-5");

		for (final var filler : fillers) {
			for (final var value : values) {
				for (var offset = 0; offset < 2400; offset += 17) {
					final var message = filler.repeat(offset / filler.length() + 1).substring(0, offset) + value + " " + filler.repeat(10);
					final var whole = PiiMasker.maskPii(message, categories);

					assertThat(converter.convert(event(message)))
						.as("%s after %d characters of '%s'", value, offset, filler)
						.isEqualTo(whole.substring(0, Math.min(100, whole.length())));
				}
			}
		}
	}

	@Test
	void masksAnAddressLongerThanAWindow() {
		// A window that cut the address would leave the beginning of its local part in clear
		final var message = "x".repeat(2000) + "@example.com";

		assertThat(startedConverter("true", "100").convert(event(message))).isEqualTo("x***@example.com");
	}

	@Test
	void masksAPhoneNumberBeforeARunOfDigitsThatAWindowCuts() {
		// Cut by a window, an unlimited fraction of a second would take the phone number for a date, which is left alone
		final var message = "Call 0701-23-45." + "1".repeat(3000) + "-5 done";

		assertThat(startedConverter("true", "100").convert(event(message))).isEqualTo(("Call ****-**-**." + "1".repeat(3000)).substring(0, 100));
	}

	@Test
	void masksAMessageShorterThanTheLimitWhole() {
		assertThat(startedConverter("true", "100").convert(event(MESSAGE))).isEqualTo(MASKED);
	}

	@Test
	void cutsAMessageThatIsNotMaskedAtTheLimit() {
		assertThat(startedConverter("false", "10").convert(event(MESSAGE))).isEqualTo(MESSAGE.substring(0, 10));
	}

	@Test
	void returnsNoMessageForAnEventWithout() {
		assertThat(startedConverter("true", "100").convert(event(null))).isNull();
		assertThat(startedConverter("false", "100").convert(event(null))).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"many", "-1", ""
	})
	void ignoresALimitThatIsNotANumberOfCharacters(final String option) {
		final var converter = startedConverter("true", option);

		assertThat(converter.convert(event(MESSAGE))).isEqualTo(MASKED);
		assertThat(context.getStatusManager().getCopyOfStatusList())
			.anySatisfy(status -> assertThat(status.getMessage()).contains("Ignoring the option of %maskPii{" + option + "}"));
	}

	@Test
	void readsTheLimitThroughPatternLayout() {
		context.putProperty(PiiMaskingConverter.ENABLED_PROPERTY, "true");

		final var layout = new PatternLayout();
		layout.setContext(context);
		layout.getInstanceConverterMap().put("maskPii", PiiMaskingConverter::new);
		layout.setPattern("%maskPii{20}");
		layout.start();

		assertThat(layout.doLayout(event(MESSAGE))).isEqualTo(MASKED.substring(0, 20));
	}

	private PiiMaskingConverter startedConverter(final String enabledValue) {
		return startedConverter(enabledValue, null);
	}

	private PiiMaskingConverter startedConverter(final String enabledValue, final String option) {
		if (enabledValue != null) {
			context.putProperty(PiiMaskingConverter.ENABLED_PROPERTY, enabledValue);
		}
		final var converter = new PiiMaskingConverter();
		converter.setContext(context);
		if (option != null) {
			converter.setOptionList(List.of(option));
		}
		converter.start();
		return converter;
	}

	private ILoggingEvent event(final String message) {
		return new LoggingEvent(getClass().getName(), context.getLogger("test"), Level.INFO, message, null, null);
	}
}
