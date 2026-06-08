package se.sundsvall.dept44.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.support.Identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration test verifying the acceptance criteria: an {@code @Async} task initiated from an HTTP handler observes
 * the
 * same {@code x-request-id} (and {@code X-Sent-By} identity) as the calling request thread, purely via the
 * auto-configured {@link MdcTaskDecorator} - no per-service MDC wiring.
 * <p>
 * The test also asserts that the task genuinely ran on a <em>different</em> thread from the application task executor
 * (and not synchronously on the request thread), so a regression where {@code @Async} silently degrades to synchronous
 * execution cannot make the test pass for the wrong reason.
 */
@SpringBootTest(classes = AsyncMdcPropagationTest.TestApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class AsyncMdcPropagationTest {

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void asyncTaskInheritsRequestIdAndIdentifierFromHttpHandler() {
		webTestClient.get().uri("/async-context")
			.header(RequestId.HEADER_NAME, "it-request-id-123")
			.header(Identifier.HEADER_NAME, "joe001doe; type=adAccount")
			.exchange()
			.expectStatus().isOk()
			// the request thread still echoes the x-request-id back
			.expectHeader().valueEquals(RequestId.HEADER_NAME, "it-request-id-123")
			// the body is the context observed on the @Async worker thread - it must match the request
			.expectBody(String.class).value(body -> {
				// format: requestId|sentBy|sentByType|workerThread=<name>|requestThread=<name>
				final var parts = body.split("\\|");
				assertThat(parts).hasSize(5);
				assertThat(parts[0]).isEqualTo("it-request-id-123");
				assertThat(parts[1]).isEqualTo("joe001doe");
				assertThat(parts[2]).isEqualTo("adAccount");

				final var workerThread = parts[3].substring("workerThread=".length());
				final var requestThread = parts[4].substring("requestThread=".length());
				// proves the task really ran on the application task executor (default prefix "task-")
				// and not synchronously on the request thread
				assertThat(workerThread)
					.startsWith("task-")
					.isNotEqualTo(requestThread);
			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableAsync
	static class TestApplication {

		@Bean
		AsyncContextCapturer asyncContextCapturer() {
			return new AsyncContextCapturer();
		}

		@Bean
		AsyncContextController asyncContextController(final AsyncContextCapturer capturer) {
			return new AsyncContextController(capturer);
		}
	}

	@RestController
	static class AsyncContextController {

		private final AsyncContextCapturer capturer;

		AsyncContextController(final AsyncContextCapturer capturer) {
			this.capturer = capturer;
		}

		@GetMapping(value = "/async-context", produces = MediaType.TEXT_PLAIN_VALUE)
		String asyncContext() throws Exception {
			final var workerContext = capturer.captureOnAsyncThread().get(10, TimeUnit.SECONDS);
			return workerContext + "|requestThread=" + Thread.currentThread().getName();
		}
	}

	static class AsyncContextCapturer {

		/**
		 * Runs on a worker thread from the application task executor. The returned value reflects the MDC/identity (and the
		 * thread name) as seen on that thread - which must equal the calling request thread's context while running on a
		 * different thread.
		 */
		@Async
		CompletableFuture<String> captureOnAsyncThread() {
			final var identifier = Identifier.get();
			final var sentBy = identifier != null ? identifier.getValue() : "null";
			final var sentByType = identifier != null ? identifier.getTypeString() : "null";
			return CompletableFuture.completedFuture(
				RequestId.get() + "|" + sentBy + "|" + sentByType + "|workerThread=" + Thread.currentThread().getName());
		}
	}
}
