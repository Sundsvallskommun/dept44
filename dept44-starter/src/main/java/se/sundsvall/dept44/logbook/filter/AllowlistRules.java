package se.sundsvall.dept44.logbook.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The paths of {@code logbook.body-filters.allowlist.paths}, compiled into something a walk of a body can ask per
 * field.
 * <p>
 * A path is the part of JSONPath that a field can be named by, and no more. {@code $} is the root, {@code .field} a
 * child, {@code ..field} a descendant at any depth, {@code *} any one field, and {@code [*]} the elements of an array.
 * A field whose name holds a '.' is named in brackets: {@code $.Data['PriorityInfo.Priority']} or
 * {@code $..["PriorityInfo.Priority"]}. A predicate, a slice, an index, a union or a function is refused when the paths
 * are compiled, rather than quietly never matching.
 * <p>
 * {@code $..errandNumber} therefore keeps that field wherever it occurs, and {@code $.content[*].title} keeps the title
 * of the rows of a page and nothing else. Every path must start with {@code $}, so that the difference between the two
 * is visible on every line of a list that people maintain by hand.
 * <p>
 * Array nesting is transparent, so {@code [*]} may be left out: {@code $.content.title} and {@code $.content[*].title}
 * are the same path. It follows that {@code [*]} does not require an array. {@code $[*].name} also keeps the
 * {@code name} of an object at the root, and {@code $.content[*].title} the {@code title} of a {@code content} object.
 * A
 * path is anchored by its fields, not by its arrays.
 * <p>
 * A path only holds one {@code ..}. Matching a path with several costs, for every value, a power of how deeply the body
 * nests, and the sender decides that.
 * <p>
 * A path names a field that holds a single value, or an array of single values, which it then keeps all of. A path to
 * an object, or to an array of objects, keeps nothing below it, so that a field added to it later is not logged without
 * anyone asking for it. {@code $.address..*} keeps every value below {@code address}.
 */
final class AllowlistRules {

	private static final String INVALID_PATH = "Invalid path '%s' in logbook.body-filters.allowlist.paths: %s";
	private static final String UNSUPPORTED_BRACKET = "a predicate, a slice or an index cannot be used. Only '[*]' and a quoted name such as ['a.b'] are supported";
	private static final String NO_FIELD_AFTER_ROOT = "it must continue with '.', '..' or '[' after the root";

	/** The elements of an array, which the walk of a body does not count as a field of their own. */
	private static final String ELEMENTS = "[*]";

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

		// Read from left to right rather than split on '.', since a quoted name may hold a '.', a '[*]' or a '('
		final var segments = new ArrayList<String>();
		var i = 1;
		while (i < path.length()) {
			if (path.startsWith(ELEMENTS, i)) {
				// Array nesting is transparent, so the marker adds no segment of its own
				i = afterElements(path, i);
			} else if (isQuotedName(path, i)) {
				i = readQuotedName(path, i, segments);
			} else if (path.startsWith(DESCENDANT, i)) {
				if (segments.contains(DESCENDANT)) {
					throw invalid(path, "only one '..' can be used, since each one more multiplies what matching a deeply nested body costs");
				}
				segments.add(DESCENDANT);
				i += DESCENDANT.length();
				i = isQuotedName(path, i) ? readQuotedName(path, i, segments) : readName(path, i, segments);
			} else if (path.charAt(i) == '.') {
				i = readName(path, i + 1, segments);
			} else if (path.charAt(i) == '[') {
				throw invalid(path, UNSUPPORTED_BRACKET);
			} else {
				// Every segment ends where the next can begin, so only the first character after the root can get here
				throw invalid(path, NO_FIELD_AFTER_ROOT);
			}
		}
		if (segments.isEmpty()) {
			throw invalid(path, NO_FIELD_AFTER_ROOT);
		}

		// A '..' is always followed by a field, since reading one is what the loop does next, so the last segment of a
		// compiled rule is a field or '*' and never a '..'
		return segments.toArray(String[]::new);
	}

	private static int afterElements(final String path, final int from) {
		final var end = from + ELEMENTS.length();
		if (!atBoundary(path, end)) {
			throw invalid(path, "'[*]' must be followed by '.' or end the path");
		}
		return end;
	}

	private static int readName(final String path, final int from, final List<String> segments) {
		var end = from;
		while (!atBoundary(path, end)) {
			end++;
		}
		final var name = path.substring(from, end);
		if (name.isEmpty()) {
			throw invalid(path, "it names an empty field");
		}
		if (name.indexOf(']') >= 0) {
			throw invalid(path, UNSUPPORTED_BRACKET);
		}
		if (name.indexOf('(') >= 0) {
			throw invalid(path, "a function cannot be used");
		}
		segments.add(name);
		return end;
	}

	/**
	 * Reads a name in brackets, such as {@code ['PriorityInfo.Priority']}, which is how a field whose name holds a '.' is
	 * named. Escapes are not read, so a name that holds a quote is written in the other kind.
	 */
	private static int readQuotedName(final String path, final int from, final List<String> segments) {
		final var quote = path.charAt(from + 1);
		final var close = path.indexOf(quote + "]", from + 2);
		if (close < 0) {
			throw invalid(path, "a quoted name must end with %c]".formatted(quote));
		}
		final var name = path.substring(from + 2, close);
		if (name.isEmpty()) {
			throw invalid(path, "it names an empty field");
		}
		// A union such as ['a','b'] ends up here too, as a name that holds its own quote
		if (name.indexOf(quote) >= 0 || name.indexOf('\\') >= 0) {
			throw invalid(path, "a quoted name cannot hold its own quote or a '\\'. Write a name that holds ' in double quotes, and the other way around");
		}
		// They stand for the wildcard and the descendant in a compiled rule, so a field by either name would keep far more
		if (ANY.equals(name) || DESCENDANT.equals(name)) {
			throw invalid(path, "a quoted name cannot be '*' or '..'");
		}
		final var end = close + 2;
		if (!atBoundary(path, end)) {
			throw invalid(path, "a quoted name must be followed by '.', '[' or end the path");
		}
		segments.add(name);
		return end;
	}

	private static boolean isQuotedName(final String path, final int at) {
		return path.startsWith("['", at) || path.startsWith("[\"", at);
	}

	/**
	 * @return whether a segment that ends at {@code at} is followed by the start of another, or by the end of the path
	 */
	private static boolean atBoundary(final String path, final int at) {
		return at == path.length() || path.charAt(at) == '.' || path.charAt(at) == '[';
	}

	private static IllegalArgumentException invalid(final String path, final String reason) {
		return new IllegalArgumentException(INVALID_PATH.formatted(path, reason));
	}
}
