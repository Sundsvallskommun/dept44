package se.sundsvall.dept44.configuration.webclient;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.ResponseFilter;
import org.zalando.logbook.Sink;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * Runs a real WebClient against a server, with a real Logbook. What the sink receives as a body is what Logbook held in
 * memory for it.
 */
class LogbookCaptureLimitTest {

	private static final int LIMIT = 100;

	private final AtomicReference<String> loggedBody = new AtomicReference<>();
	private final BodyCapturePolicy policy = new BodyCapturePolicy(LIMIT);
	private MockWebServer server;

	@BeforeEach
	void startServer() throws IOException {
		server = new MockWebServer();
		server.start();
	}

	@AfterEach
	void stopServer() throws IOException {
		server.shutdown();
	}

	@Test
	void smallChunkedResponseIsLoggedInFull() {
		server.enqueue(new MockResponse().setHeader(CONTENT_TYPE, "text/plain").setChunkedBody(text(LIMIT), 7));

		final var received = webClient().get().retrieve().bodyToMono(String.class).block();

		assertThat(received).isEqualTo(text(LIMIT));
		await().untilAsserted(() -> assertThat(loggedBody.get()).isEqualTo(text(LIMIT)));
	}

	@Test
	void largeChunkedResponseIsLoggedAsOmittedAndReceivedInFull() {
		server.enqueue(new MockResponse().setHeader(CONTENT_TYPE, "application/json").setChunkedBody(text(LIMIT * 50), 64));

		final var received = webClient().get().retrieve().bodyToMono(String.class).block();

		assertThat(received).isEqualTo(text(LIMIT * 50));
		await().untilAsserted(() -> assertThat(loggedBody.get()).isEqualTo("{\"bodyOmitted\":\"larger than 100 bytes\"}"));
	}

	@Test
	void binaryResponseIsNotCopied() {
		final var binary = new Buffer().write(new byte[LIMIT * 5]);
		server.enqueue(new MockResponse().setHeader(CONTENT_TYPE, "application/pdf").setBody(binary));

		final var received = webClient().get().retrieve().bodyToMono(byte[].class).block();

		assertThat(received).hasSize(LIMIT * 5);
		await().untilAsserted(() -> assertThat(loggedBody.get()).isEmpty());
	}

	@Test
	void responsesOnAReusedConnectionAreLimitedEachOnTheirOwn() {
		server.enqueue(new MockResponse().setHeader(CONTENT_TYPE, "text/plain").setChunkedBody(text(LIMIT * 3), 64));
		server.enqueue(new MockResponse().setHeader(CONTENT_TYPE, "text/plain").setChunkedBody(text(LIMIT / 2), 64));
		final var webClient = webClient();

		webClient.get().retrieve().bodyToMono(String.class).block();
		await().untilAsserted(() -> assertThat(loggedBody.get()).isEqualTo("<body omitted: larger than 100 bytes>"));
		final var second = webClient.get().retrieve().bodyToMono(String.class).block();

		assertThat(second).isEqualTo(text(LIMIT / 2));
		await().untilAsserted(() -> assertThat(loggedBody.get()).isEqualTo(text(LIMIT / 2)));
	}

	@Test
	void hiddenChunksAreEqualWhenTheyHideEqualChunks() {
		final var original = new DefaultHttpContent(Unpooled.copiedBuffer("a", UTF_8));
		final var other = new DefaultHttpContent(Unpooled.copiedBuffer("b", UTF_8));

		assertThat(new LogbookCaptureLimit.HiddenContent(original))
			.isEqualTo(new LogbookCaptureLimit.HiddenContent(original))
			.hasSameHashCodeAs(new LogbookCaptureLimit.HiddenContent(original))
			.isNotEqualTo(new LogbookCaptureLimit.HiddenContent(other))
			.isNotEqualTo(original);
		assertThat(new LogbookCaptureLimit.HiddenLastContent(original, Unpooled.copiedBuffer("shown", UTF_8)))
			.isEqualTo(new LogbookCaptureLimit.HiddenLastContent(original, Unpooled.copiedBuffer("shown", UTF_8)))
			.hasSameHashCodeAs(new LogbookCaptureLimit.HiddenLastContent(original, Unpooled.copiedBuffer("shown", UTF_8)))
			.isNotEqualTo(new LogbookCaptureLimit.HiddenLastContent(other, Unpooled.copiedBuffer("shown", UTF_8)))
			.isNotEqualTo(original);
	}

	private org.springframework.web.reactive.function.client.WebClient webClient() {
		final var logbook = Logbook.builder()
			.strategy(new BodyCaptureStrategy(policy))
			.responseFilter(ResponseFilter.none())
			.sink(new Sink() {
				@Override
				public void write(final Precorrelation precorrelation, final HttpRequest request) {
					// Only responses are of interest here
				}

				@Override
				public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) throws IOException {
					loggedBody.set(new String(response.getBody(), UTF_8));
				}
			})
			.build();

		return new WebClientBuilder()
			.withBaseUrl(server.url("/").toString())
			.withLogbook(logbook, policy)
			.build();
	}

	private static String text(final int size) {
		return "a".repeat(size);
	}
}
