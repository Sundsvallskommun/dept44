package se.sundsvall.dept44.logbook.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The paths of {@code logbook.body-filters.allowlist.paths}, compiled into something a walk of a body can ask per
 * field.
 * <p>
 * A path is the part of JSONPath that a field can be named by, and no more. {@code $} is the root, {@code .field} a
 * child, {@code ..field} a descendant at any depth, {@code *} any one field, and {@code [*]} the elements of an array.
 * A predicate, a slice, an index or a function is refused when the paths are compiled, rather than quietly never
 * matching.
 * <p>
 * {@code $..errandNumber} therefore keeps that field wherever it occurs, and {@code $.content[*].title} keeps the title
 * of the rows of a page and nothing else. Every path must start with {@code $}, so that the difference between the two
 * is visible on every line of a list that people maintain by hand.
 * <p>
 * Array nesting is transparent, so {@code [*]} may be left out: {@code $.content.title} and {@code $.content[*].title}
 * are the same path.
 * <p>
 * A path names a field that holds a single value. A path to an object or an array keeps nothing below it, so that a
 * field added to it later is not logged without anyone asking for it. {@code $.address..*} keeps every value below
 * {@code address}.
 */
final class AllowlistRules {

	private static final String INVALID_PATH = "Invalid path '%s' in logbook.body-filters.allowlist.paths: %s";

	/** A {@code [*]} directly followed by a field name, which removing it would join to the field before it. */
	private static final Pattern ELEMENTS_BEFORE_FIELD = Pattern.compile("\\[\\*](?![.\\[]|$)");

	/** Stands for {@code ..} in a compiled rule: any number of fields, including none, before the next segment. */
	private static final String DESCENDANT = "..";

	/** Stands for {@code *} in a compiled rule: exactly one field, whatever it is called. */
	private static final String ANY = "*";

	/** Rules by the field they end in, so that a field is only matched against the rules that could name it. */
	private final Map<String, List<String[]>> byField;

	/** Rules ending in {@code *}, which no field name can be indexed by. */
	private final List<String[]> endingInAny;

	private AllowlistRules(final Map<String, List<String[]>> byField, final List<String[]> endingInAny) {
		this.byField = byField;
		this.endingInAny = endingInAny;
	}

	/**
	 * @throws IllegalArgumentException if a path is not within the supported syntax
	 */
	static AllowlistRules of(final Collection<String> paths) {
		final var byField = new HashMap<String, List<String[]>>();
		final var endingInAny = new ArrayList<String[]>();

		// Compiled once, at startup, and only read afterwards: the rules are shared by every thread that logs
		paths.stream().map(AllowlistRules::compile).forEach(rule -> {
			final var last = rule[rule.length - 1];
			if (ANY.equals(last)) {
				endingInAny.add(rule);
			} else {
				byField.computeIfAbsent(last, _ -> new ArrayList<>()).add(rule);
			}
		});

		return new AllowlistRules(byField, endingInAny);
	}

	/**
	 * @param  path the fields from the root of the body down to and including the one asked about. An array is not a
	 *              field of its own, so the values of {@code recipients} are asked about as {@code recipients}.
	 * @return      whether the value of the field at this path is logged
	 */
	boolean keeps(final List<String> path) {
		// The values of an array at the root of a body are under no field at all, and no rule can name them
		if (path.isEmpty()) {
			return false;
		}

		final var rules = byField.get(path.getLast());
		if (rules != null) {
			for (final var rule : rules) {
				if (matches(rule, 0, path, 0)) {
					return true;
				}
			}
		}
		for (final var rule : endingInAny) {
			if (matches(rule, 0, path, 0)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(final String[] rule, final int r, final List<String> path, final int p) {
		if (r == rule.length) {
			return p == path.size();
		}
		if (DESCENDANT.equals(rule[r])) {
			for (var skipped = p; skipped <= path.size(); skipped++) {
				if (matches(rule, r + 1, path, skipped)) {
					return true;
				}
			}
			return false;
		}
		return p < path.size()
			&& (ANY.equals(rule[r]) || rule[r].equals(path.get(p)))
			&& matches(rule, r + 1, path, p + 1);
	}

	private static String[] compile(final String path) {
		if (path == null || path.isBlank()) {
			throw invalid(path, "a path cannot be blank");
		}
		if (!path.startsWith("$")) {
			throw invalid(path, "it is not a path. Write '$..%s' to keep the field wherever it occurs, or anchor it from the root".formatted(path));
		}

		final var withElements = path.substring(1);
		if (ELEMENTS_BEFORE_FIELD.matcher(withElements).find()) {
			throw invalid(path, "'[*]' must be followed by '.' or end the path");
		}
		final var fields = withElements.replace("[*]", "");
		refuse(fields, path);

		final var segments = new ArrayList<String>();
		var i = 0;
		while (i < fields.length()) {
			if (fields.startsWith(DESCENDANT, i)) {
				segments.add(DESCENDANT);
				i += 2;
			} else if (fields.charAt(i) == '.') {
				i++;
			} else {
				throw invalid(path, "a field must follow a '.'");
			}
			final var end = indexOfDot(fields, i);
			final var field = fields.substring(i, end);
			if (field.isEmpty()) {
				throw invalid(path, "it names an empty field");
			}
			segments.add(field);
			i = end;
		}

		// A '..' is always followed by a field, since reading one is what the loop does next, so the last segment of a
		// compiled rule is a field or '*' and never a '..'
		return segments.toArray(String[]::new);
	}

	private static int indexOfDot(final String fields, final int from) {
		final var dot = fields.indexOf('.', from);
		return dot < 0 ? fields.length() : dot;
	}

	private static void refuse(final String fields, final String path) {
		if (fields.indexOf('[') >= 0 || fields.indexOf(']') >= 0) {
			throw invalid(path, "a predicate, a slice or an index cannot be used. Only '[*]' is supported, and it may be left out");
		}
		if (fields.indexOf('(') >= 0) {
			throw invalid(path, "a function cannot be used");
		}
		if (!fields.startsWith(".")) {
			throw invalid(path, "it must continue with '.' or '..' after the root");
		}
	}

	private static IllegalArgumentException invalid(final String path, final String reason) {
		return new IllegalArgumentException(INVALID_PATH.formatted(path, reason));
	}
}
