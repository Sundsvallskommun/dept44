package se.sundsvall.dept44.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.EnumSet;
import java.util.Set;
import se.sundsvall.dept44.util.PiiMasker;
import se.sundsvall.dept44.util.PiiMasker.Category;

/**
 * Logback pattern converter that masks PII in the rendered message of every log event, so that personal data is hidden
 * even when a developer forgets to call {@link PiiMasker} manually.
 *
 * <p>
 * It is registered as a conversion word (e.g. {@code maskPii}) via a {@code <conversionRule>} and used in place of
 * {@code %m} in pattern layouts. Masking is opt-in: it is only active when the logback context property
 * {@value #ENABLED_PROPERTY} is {@code true} (wired from the Spring property
 * {@code dept44.logback.pii-masking.enabled}). When disabled, {@link #convert(ILoggingEvent)} returns the message
 * unchanged, so the output is identical to {@code %m}.
 *
 * <p>
 * Each category can be turned off on its own, from {@code dept44.logback.pii-masking.uuid},
 * {@code .personal-number}, {@code .phone-number} and {@code .email}. A category is masked unless it is explicitly set
 * to {@code false}, so a service that says nothing, and one whose own {@code logback-spring.xml} does not carry these
 * properties, masks everything as before. Turning {@code uuid} off is what lets a service keep the identifiers it
 * traces an incident by - an {@code errandId} or an {@code attachmentId} is a UUID too, and masking leaves four
 * characters of it.
 *
 * <p>
 * The flags are read once in {@link #start()} and cached - there is no per-line property lookup.
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
 * @see PiiMasker#maskPii(String, Set)
 */
public class PiiMaskingConverter extends ClassicConverter {

	/** Logback context property that toggles masking. Sourced from {@code dept44.logback.pii-masking.enabled}. */
	static final String ENABLED_PROPERTY = "DEPT44_PII_MASKING_ENABLED";

	/** What a category's property has to say for that category to be left alone. Anything else, absence included, masks. */
	private static final String DISABLED = "false";

	private boolean enabled;

	private Set<Category> categories;

	/** The logback context property each category is turned off from. */
	static String propertyOf(final Category category) {
		return "DEPT44_PII_MASKING_" + category.name();
	}

	@Override
	public void start() {
		this.enabled = isEnabled();
		this.categories = readCategories();
		super.start();
	}

	@Override
	public String convert(final ILoggingEvent event) {
		final var message = event.getFormattedMessage();
		return enabled ? PiiMasker.maskPii(message, categories) : message;
	}

	/** Read the way the categories are, so that surrounding whitespace means the same to both. */
	private boolean isEnabled() {
		final var value = getContext().getProperty(ENABLED_PROPERTY);
		return value != null && Boolean.parseBoolean(value.strip());
	}

	private Set<Category> readCategories() {
		final var categoriesSet = EnumSet.noneOf(Category.class);
		for (final var category : Category.values()) {
			if (!isTurnedOff(category)) {
				categoriesSet.add(category);
			}
		}
		return categoriesSet;
	}

	private boolean isTurnedOff(final Category category) {
		final var value = getContext().getProperty(propertyOf(category));
		return value != null && DISABLED.equalsIgnoreCase(value.strip());
	}
}
