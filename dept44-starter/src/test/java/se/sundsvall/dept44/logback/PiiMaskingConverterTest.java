package se.sundsvall.dept44.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingConverterTest {

	private static final String MESSAGE = "User john.doe@example.com partyId f47ac10b-58cc-4372-a567-0e02b2c3d479 ssn 900101-1234";
	private static final String MASKED = "User j***@example.com partyId f47a... ssn ******-****";

	private final LoggerContext context = new LoggerContext();

	@Test
	void masksWhenEnabled() {
		assertThat(startedConverter("true").convert(event(MESSAGE))).isEqualTo(MASKED);
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
