package se.sundsvall.dept44.benchmarks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Allowlist paths for the benchmarks.
 */
public final class AllowlistPaths {

	/**
	 * The kinds of path, which differ in how many rules a value of a body is matched against and how far each of them
	 * has to look. A value is only matched against the rules that end in its field name, and against every rule that
	 * ends in {@code *}.
	 */
	public enum RuleKind {
		/** {@code $..fieldN}: each rule ends in a field name of its own, so a value is matched against at most one. */
		DESCENDANT,
		/** {@code $.content[*].fieldN}: as above, anchored at the root. */
		ANCHORED,
		/** {@code $..groupN.field0}: every rule ends in {@code field0}, so a {@code field0} is matched against all. */
		SHARED_LEAF,
		/** {@code $..groupN.*}: every value is matched against every rule. */
		WILDCARD
	}

	private static final String SUPPORT_MANAGEMENT = "/support-management-allowlist.txt";

	private AllowlistPaths() {}

	/**
	 * @param  kind  the kind of path
	 * @param  count the number of paths
	 * @return       the paths
	 */
	public static List<String> of(final RuleKind kind, final int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> path(kind, i))
			.toList();
	}

	private static String path(final RuleKind kind, final int i) {
		return switch (kind) {
			case DESCENDANT -> "$..field" + i;
			case ANCHORED -> "$.content[*].field" + i;
			case SHARED_LEAF -> "$..group" + i + ".field0";
			case WILDCARD -> "$..group" + i + ".*";
		};
	}

	/**
	 * @return the 183 paths that api-service-support-management keeps
	 */
	public static List<String> supportManagement() {
		final var resource = requireNonNull(AllowlistPaths.class.getResourceAsStream(SUPPORT_MANAGEMENT), SUPPORT_MANAGEMENT);
		try (final var reader = new BufferedReader(new InputStreamReader(resource, UTF_8))) {
			return reader.lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.toList();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
