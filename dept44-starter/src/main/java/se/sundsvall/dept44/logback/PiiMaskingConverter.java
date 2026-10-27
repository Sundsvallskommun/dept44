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
 * unmasked, so the output is identical to {@code %m}, cut to the length an option gives.
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
 * An option limits what is returned to that many characters: {@code %maskPii{100}} returns the same first 100
 * characters as {@code %.-100maskPii}, but masks only as much of the message as that takes, rather than all of it. A
 * payload line can be a megabyte, so this is what keeps a short message, such as the one GELF sends, from costing as
 * much as the full one.
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

	/**
	 * Longer than any value {@link PiiMasker} matches, apart from an e-mail address, whose parts have no length limit. The
	 * longest of the others is a UUID, of 36 characters.
	 */
	static final int LONGEST_MATCH = 512;

	/** The characters of an e-mail address that are not letters or digits, as {@link PiiMasker} reads one. */
	private static final String ADDRESS_PUNCTUATION = "._%+-@";

	/** The most characters at the end of a masked window that may differ from the whole message masked. */
	private static final int UNSURE_TAIL = LONGEST_MATCH * 3 / 2;

	private static final int NO_LIMIT = -1;

	private boolean enabled;

	private Set<Category> categories;

	private int maxLength;

	/** The logback context property each category is turned off from. */
	static String propertyOf(final Category category) {
		return "DEPT44_PII_MASKING_" + category.name();
	}

	@Override
	public void start() {
		this.enabled = isEnabled();
		this.categories = readCategories();
		this.maxLength = readMaxLength();
		super.start();
	}

	@Override
	public String convert(final ILoggingEvent event) {
		final var message = event.getFormattedMessage();
		if (!enabled) {
			return truncate(message);
		}
		return maxLength == NO_LIMIT ? PiiMasker.maskPii(message, categories) : truncate(maskBeginning(message));
	}

	/**
	 * Masks a window at the start of the message, large enough that its first {@link #maxLength} characters masked are
	 * the same as those of the whole message masked.
	 * <p>
	 * A window never ends inside what could be an e-mail address, since an address has no length limit: it ends at the
	 * first character from its size on that no address holds. A message that is one long run of such characters is
	 * masked whole. Any other value that the end of a window cuts in two is not recognised, so the masked text of the
	 * last {@link #LONGEST_MATCH} characters of a window may be wrong. Masking makes that text at most half as long
	 * again, as {@code a@b.se} becomes {@code a***@b.se}, so all but the last {@link #UNSURE_TAIL} characters of a masked
	 * window are right. A window starts large enough for that to cover the limit unless masking shortens it by a lot,
	 * and one that masking does shorten that much, such as one full of UUIDs, is doubled.
	 */
	private String maskBeginning(final String message) {
		if (message != null) {
			var window = (long) maxLength + 2 * LONGEST_MATCH;
			while (window < message.length()) {
				final var end = endOfAddressCharacters(message, (int) window);
				final var masked = PiiMasker.maskPii(message.substring(0, end), categories);
				if (end == message.length() || masked.length() - UNSURE_TAIL >= maxLength) {
					return masked;
				}
				window = 2L * end;
			}
		}
		return PiiMasker.maskPii(message, categories);
	}

	/** The index of the first character at or after the given one that no e-mail address holds, or the length. */
	private static int endOfAddressCharacters(final String message, final int from) {
		var index = from;
		while (index < message.length() && isAddressCharacter(message.charAt(index))) {
			index++;
		}
		return index;
	}

	private static boolean isAddressCharacter(final char character) {
		return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')
			|| ADDRESS_PUNCTUATION.indexOf(character) >= 0;
	}

	private String truncate(final String message) {
		return maxLength != NO_LIMIT && message != null && message.length() > maxLength ? message.substring(0, maxLength) : message;
	}

	/**
	 * The option, such as the 100 of {@code %maskPii{100}}: the most characters to return. Without one, there is no limit.
	 */
	private int readMaxLength() {
		final var option = getFirstOption();
		if (option == null) {
			return NO_LIMIT;
		}
		try {
			final var value = Integer.parseInt(option.strip());
			if (value >= 0) {
				return value;
			}
		} catch (final NumberFormatException _) {
			// Reported below, as a negative number is
		}
		addError("Ignoring the option of %%maskPii{%s}, which is not a number of characters".formatted(option));
		return NO_LIMIT;
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
