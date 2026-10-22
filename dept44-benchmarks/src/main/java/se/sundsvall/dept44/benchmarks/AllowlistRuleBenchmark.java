package se.sundsvall.dept44.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import se.sundsvall.dept44.benchmarks.AllowlistPaths.RuleKind;
import se.sundsvall.dept44.logbook.filter.AllowlistBodyFilter;

/**
 * How the kind of path and the shape of the body change what the allowlist filter costs, at 200 paths and a 100 KiB
 * body.
 * <p>
 * Paths that each end in a field name of their own are cheap however many there are, since a value is only matched
 * against the rules that end in its name. Many rules that end in the same name, or in {@code *}, are matched against
 * every value they could name, which is where the number of paths starts to cost. A deeply nested body makes each
 * {@code ..} look further.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class AllowlistRuleBenchmark {

	private static final int PATHS = 200;
	private static final int BODY_SIZE = 100 * 1024;

	public enum Shape {
		/** One object with a field of its own for every value. */
		FLAT,
		/** Objects nested {@value Payloads#DEPTH} levels deep. */
		NESTED,
		/** A page of rows of twenty fields. */
		PAGE
	}

	@Param
	public RuleKind ruleKind;

	@Param
	public Shape shape;

	private AllowlistBodyFilter filter;
	private String body;

	@Setup
	public void setUp() {
		filter = new AllowlistBodyFilter(AllowlistPaths.of(ruleKind, PATHS), -1);
		body = switch (shape) {
			case FLAT -> Payloads.flat(BODY_SIZE);
			case NESTED -> Payloads.nested(BODY_SIZE);
			case PAGE -> Payloads.page(BODY_SIZE);
		};
	}

	@Benchmark
	public String filter() {
		return filter.filter("application/json", body);
	}
}
