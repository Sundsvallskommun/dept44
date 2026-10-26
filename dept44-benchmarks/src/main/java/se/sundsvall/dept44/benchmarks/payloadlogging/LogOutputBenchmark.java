package se.sundsvall.dept44.benchmarks.payloadlogging;

import java.io.IOException;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.zalando.logbook.Logbook;
import se.sundsvall.dept44.benchmarks.payloadlogging.LogbookChainBenchmark.Allowlist;
import se.sundsvall.dept44.benchmarks.payloadlogging.LogbookChainBenchmark.Calls;
import se.sundsvall.dept44.benchmarks.payloadlogging.LogbookContexts.StartedLogbook;

/**
 * The payload logging of {@link LogbookChainBenchmark}, written where a service writes it: to the console, and to a
 * log server over GELF, with or without PII masking. This is what the appenders of logback-spring.xml add on the
 * thread that handles the request, on top of {@link LogOutput#DISCARD}.
 * <p>
 * GELF compresses each line and sends it in chunks of 508 bytes. A line that would take more than the 128 chunks GELF
 * allows is sent in larger chunks instead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class LogOutputBenchmark {

	@Param
	public LogOutput output;

	@Param({
		"DISABLED", "EMPTY"
	})
	public Allowlist allowlist;

	@Param({
		"1", "10", "100", "250", "300"
	})
	public int bodyKiB;

	private StartedLogbook started;
	private Logbook logbook;
	private String body;

	@Setup
	public void setUp() {
		started = LogbookContexts.start(LogbookChainBenchmark.properties(allowlist), output);
		logbook = started.logbook();
		body = Payloads.page(bodyKiB * 1024);
	}

	@TearDown
	public void tearDown() {
		started.close();
	}

	@Benchmark
	public void logCall() throws IOException {
		logbook.process(Calls.request())
			.write()
			.process(Calls.response(body))
			.write();
	}
}
