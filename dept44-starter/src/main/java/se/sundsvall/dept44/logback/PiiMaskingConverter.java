package se.sundsvall.dept44.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import se.sundsvall.dept44.util.PiiMasker;

/**
 * Logback pattern converter that masks PII in the rendered message of every log event, so that personal data is hidden
 * even when a developer forgets to call {@link PiiMasker} manually.
 *
 * <p>
 * It is registered as a conversion word (e.g. {@code maskPii}) via a {@code <conversionRule>} and used in place of
 * {@code %m} in pattern layouts. Masking is opt-in: it is only active when the logback context property
 * {@value #ENABLED_PROPERTY} is {@code true} (wired from the Spring property
 * {@code dept44.logback.pii-masking.enabled}). When disabled, {@link #convert(ILoggingEvent)} returns the message
 * unchanged, so the output is identical to {@code %m}. The flag is read once in {@link #start()} and cached - there is
 * no per-line property lookup.
 *
 * <p>
 * <strong>Why a converter and not a {@code Filter}:</strong> this component fulfils the requirement of a
 * "PII masking filter", but it is deliberately implemented as a {@link ClassicConverter} rather than a logback
 * {@code Filter}/{@code TurboFilter}. A logback filter can only accept or deny an event; it cannot rewrite the message
 * text ({@link ILoggingEvent} exposes no message setter). A pattern converter is the only extension point that can
 * transform the rendered content of every log line.
 *
 * <p>
 * <strong>Scope:</strong> this masks the rendered <em>message</em> only. Fields the GELF encoder emits separately - MDC
 * values, caller data and exception/stack-trace content - do not pass through this converter and are therefore not
 * masked by it.
 *
 * @see PiiMasker#maskPii(String)
 */
public class PiiMaskingConverter extends ClassicConverter {

	/** Logback context property that toggles masking. Sourced from {@code dept44.logback.pii-masking.enabled}. */
	static final String ENABLED_PROPERTY = "DEPT44_PII_MASKING_ENABLED";

	private boolean enabled;

	@Override
	public void start() {
		this.enabled = Boolean.parseBoolean(getContext().getProperty(ENABLED_PROPERTY));
		super.start();
	}

	@Override
	public String convert(final ILoggingEvent event) {
		final var message = event.getFormattedMessage();
		return enabled ? PiiMasker.maskPii(message) : message;
	}
}
