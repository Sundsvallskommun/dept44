package se.sundsvall.dept44.benchmarks;

import java.io.IOException;
import java.util.List;
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
import se.sundsvall.dept44.benchmarks.LogbookChainBenchmark.Calls;
import se.sundsvall.dept44.benchmarks.LogbookContexts.StartedLogbook;

/**
 * The payload logging of {@link LogbookChainBenchmark} with a realistic configuration: the 183 paths of
 * api-service-support-management, against pages of errands shaped like the ones it answers with.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class SupportManagementChainBenchmark {

	public enum Allowlist {
		/** Bodies are logged in full. */
		DISABLED,
		/** The paths of api-service-support-management. */
		SUPPORT_MANAGEMENT
	}

	@Param
	public Allowlist allowlist;

	@Param({
		"1", "10", "100"
	})
	public int errands;

	/** Discarded at the logger, or written as api-service-support-management writes it. */
	@Param({
		"DISCARD", "CONSOLE_GELF_MASKED"
	})
	public LogOutput output;

	private StartedLogbook started;
	private Logbook logbook;
	private String body;

	@Setup
	public void setUp() {
		started = LogbookContexts.start(switch (allowlist) {
			case DISABLED -> List.of("logbook.body-filters.allowlist.enabled=false");
			case SUPPORT_MANAGEMENT -> List.of(LogbookContexts.allowlistPaths(AllowlistPaths.supportManagement()));
		}, output);
		logbook = started.logbook();
		body = Payloads.errandPage(errands);
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
