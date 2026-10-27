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
import tools.jackson.databind.json.JsonMapper;

/**
 * What it costs to parse a body into a tree and write it back, with nothing else done to it: the least the allowlist
 * filter can cost for a body of each size. Compared with {@link AllowlistBodyFilterBenchmark}, the difference is what
 * the walk and the paths add.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class JsonBaselineBenchmark {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Param({
		"1", "10", "100", "250", "1000"
	})
	public int bodyKiB;

	private String body;

	@Setup
	public void setUp() {
		body = Payloads.page(bodyKiB * 1024);
	}

	@Benchmark
	public String readAndWrite() {
		return JSON_MAPPER.readTree(body).toString();
	}
}
