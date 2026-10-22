package se.sundsvall.dept44.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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
import org.zalando.logbook.BodyFilter;
import se.sundsvall.dept44.logbook.filter.BodyFilterProvider;

import static se.sundsvall.dept44.benchmarks.AllowlistPaths.RuleKind.DESCENDANT;

/**
 * The json-path filters of {@code logbook.body-filters.json-path}, with the same paths and bodies as
 * {@link AllowlistBodyFilterBenchmark}, for comparison. These mask the fields they name rather than keep them.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class JsonPathFilterBenchmark {

	@Param({
		"10", "50", "200"
	})
	public int paths;

	@Param({
		"1", "10", "100", "250", "1000"
	})
	public int bodyKiB;

	private BodyFilter filter;
	private String body;

	@Setup
	public void setUp() {
		final var masks = new LinkedHashMap<String, String>();
		AllowlistPaths.of(DESCENDANT, paths).forEach(path -> masks.put(path, "***"));
		// Like the Jackson 2 mapper ObjectMapperConfiguration gives LogbookConfiguration
		filter = BodyFilterProvider.buildJsonPathFilters(new ObjectMapper().findAndRegisterModules(), masks).getFirst();
		body = Payloads.page(bodyKiB * 1024);
	}

	@Benchmark
	public String filter() {
		return filter.filter("application/json", body);
	}
}
