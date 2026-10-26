package se.sundsvall.dept44.benchmarks.payloadlogging;

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
import se.sundsvall.dept44.logbook.filter.AllowlistBodyFilter;

import static se.sundsvall.dept44.benchmarks.payloadlogging.AllowlistPaths.RuleKind.DESCENDANT;

/**
 * How the allowlist filter scales with the number of paths and the size of the body.
 * <p>
 * The body is a page of rows of twenty fields, and the paths are {@code $..fieldN}, so that the first twenty paths name
 * fields the body has and the rest name fields it does not. The maximum size is disabled, so that a body of any size is
 * parsed: that is what shows what a higher {@code logbook.body-filters.allowlist.max-size} would cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class AllowlistBodyFilterBenchmark {

	@Param({
		"0", "10", "50", "200", "500"
	})
	public int paths;

	@Param({
		"1", "10", "100", "250", "1000"
	})
	public int bodyKiB;

	private AllowlistBodyFilter filter;
	private String body;

	@Setup
	public void setUp() {
		filter = new AllowlistBodyFilter(AllowlistPaths.of(DESCENDANT, paths), -1);
		body = Payloads.page(bodyKiB * 1024);
	}

	@Benchmark
	public String filter() {
		return filter.filter("application/json", body);
	}
}
