package se.sundsvall.dept44.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.EnumSet;
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

	private PiiMaskingConverter startedConverter(final String enabledValue) {
		if (enabledValue != null) {
			context.putProperty(PiiMaskingConverter.ENABLED_PROPERTY, enabledValue);
		}
		final var converter = new PiiMaskingConverter();
		converter.setContext(context);
		converter.start();
		return converter;
	}

	private ILoggingEvent event(final String message) {
		return new LoggingEvent(getClass().getName(), context.getLogger("test"), Level.INFO, message, null, null);
	}
}
