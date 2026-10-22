package se.sundsvall.dept44.benchmarks;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.test.MockHttpRequest;
import org.zalando.logbook.test.MockHttpResponse;
import se.sundsvall.dept44.benchmarks.LogbookContexts.StartedLogbook;

import static se.sundsvall.dept44.benchmarks.AllowlistPaths.RuleKind.DESCENDANT;

/**
 * The whole payload logging of one call, as a service runs it: a GET without a body, answered with a JSON
 * body, through the {@link Logbook} that {@code LogbookConfiguration} sets up with dept44's defaults. That is
 * the capture strategy, the body filters, the allowlist, the JSON formatter with its body validation and the
 * call to the logger. The payload log is discarded at the logger. {@link LogOutputBenchmark} adds the appenders.
 * <p>
 * The defaults apply, so a body larger than the allowlist's maximum size of 256 KiB is replaced by a note rather than
 * parsed, which the 300 KiB body shows.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {
	"-Xms1g", "-Xmx1g"
})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class LogbookChainBenchmark {

	public enum Allowlist {
		/** {@code logbook.body-filters.allowlist.enabled=false}: bodies are logged in full. */
		DISABLED,
		/** The default: no paths, so every value is masked. */
		EMPTY,
		/** 50 paths, of which the first twenty name fields of the body. */
		PATHS_50,
		/** 200 paths, of which the first twenty name fields of the body. */
		PATHS_200
	}

	@Param
	public Allowlist allowlist;

	@Param({
		"1", "10", "100", "250", "300", "1000"
	})
	public int bodyKiB;

	private StartedLogbook started;
	private Logbook logbook;
	private String body;

	@Setup
	public void setUp() {
		started = LogbookContexts.start(properties(allowlist));
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

	static List<String> properties(final Allowlist allowlist) {
		return switch (allowlist) {
			case DISABLED -> List.of("logbook.body-filters.allowlist.enabled=false");
			case EMPTY -> List.of();
			case PATHS_50 -> List.of(LogbookContexts.allowlistPaths(AllowlistPaths.of(DESCENDANT, 50)));
			case PATHS_200 -> List.of(LogbookContexts.allowlistPaths(AllowlistPaths.of(DESCENDANT, 200)));
		};
	}

	/**
	 * The call that is logged, made anew for every invocation since Logbook's mocks change when told to drop their body.
	 */
	static final class Calls {

		private static final HttpHeaders REQUEST_HEADERS = HttpHeaders.of(Map.of(
			"Accept", List.of("application/json"),
			"x-request-id", List.of("9690dfc1-ea62-480b-af16-118a7a1dba85")));
		private static final HttpHeaders RESPONSE_HEADERS = HttpHeaders.of(Map.of(
			"Content-Type", List.of("application/json"),
			"x-request-id", List.of("9690dfc1-ea62-480b-af16-118a7a1dba85")));

		private Calls() {}

		static MockHttpRequest request() {
			return MockHttpRequest.create()
				.withMethod("GET")
				.withPath("/2281/errands")
				.withHeaders(REQUEST_HEADERS);
		}

		static MockHttpResponse response(final String body) {
			return MockHttpResponse.create()
				.withStatus(200)
				.withHeaders(RESPONSE_HEADERS)
				.withContentType("application/json")
				.withBodyAsString(body);
		}
	}
}
