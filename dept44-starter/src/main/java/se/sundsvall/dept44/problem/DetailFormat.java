package se.sundsvall.dept44.problem;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats the detail message of a problem by replacing each {@code {0}}, {@code {1}}, ... with the parameter at that
 * index, as its {@code toString()}. Unlike {@link java.text.MessageFormat} there is no quoting and there are no format
 * types: an apostrophe or a brace is kept as it is (so {@code "'{0}'"} gives {@code 'value'}), a number is not
 * digit-grouped, and a placeholder without a parameter, or with a format type such as {@code {0,number}}, is left as it
 * is.
 */
final class DetailFormat {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d{1,9})}");

	private DetailFormat() {}

	static String format(final String detailPattern, final Object... parameters) {
		if (detailPattern == null) {
			return null;
		}
		final var values = Objects.requireNonNullElse(parameters, new Object[0]);
		return PLACEHOLDER.matcher(detailPattern).replaceAll(placeholder -> {
			final var index = Integer.parseInt(placeholder.group(1));
			return Matcher.quoteReplacement(index < values.length ? String.valueOf(values[index]) : placeholder.group());
		});
	}
}
