package se.sundsvall.dept44.configuration.webservicetemplate.interceptor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.ResponseProcessingStage;
import org.zalando.logbook.Origin;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.ResponseFilter;
import org.zalando.logbook.Sink;
import org.zalando.logbook.httpclient5.LogbookHttpRequestInterceptor;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs a real HttpClient against a server, with Logbook's request interceptor and a real Logbook. What the sink
 * receives as a body is what was held in memory for it.
 */
class BoundedLogbookHttpResponseInterceptorTest {

	private static final int LIMIT = 100;

	private final AtomicReference<String> loggedBody = new AtomicReference<>();
	private final BodyCapturePolicy policy = new BodyCapturePolicy(LIMIT);
	private HttpServer server;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			final var size = Integer.parseInt(exchange.getRequestURI().getQuery());
			exchange.getResponseHeaders().add("Content-Type", "text/xml");
			// Length 0 makes the server send the body chunked, without Content-Length
			exchange.sendResponseHeaders(200, 0);
			try (final var body = exchange.getResponseBody()) {
				body.write("a".repeat(size).getBytes(UTF_8));
			}
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void stageAttributeIsTheOneLogbooksRequestInterceptorUses() throws Exception {
		final var attributes = Class.forName("org.zalando.logbook.httpclient5.Attributes");
		final var stage = attributes.getDeclaredField("STAGE");
		stage.setAccessible(true);

		assertThat(BoundedLogbookHttpResponseInterceptor.STAGE).isEqualTo(stage.get(null));
	}

	@Test
	void smallChunkedResponseIsLoggedInFullAndKeptInMemory() throws IOException {
		final var received = call(LIMIT);

		assertThat(received.body()).isEqualTo("a".repeat(LIMIT));
		assertThat(received.repeatable()).isTrue();
		assertThat(loggedBody.get()).isEqualTo("a".repeat(LIMIT));
	}

	@Test
	void largeChunkedResponseIsLoggedAsOmittedAndStreamedInFull() throws IOException {
		final var received = call(LIMIT * 50);

		assertThat(received.body()).isEqualTo("a".repeat(LIMIT * 50));
		assertThat(received.repeatable()).isFalse();
		assertThat(loggedBody.get()).isEqualTo("<bodyOmitted>larger than 100 bytes</bodyOmitted>");
	}

	@Test
	void unlimitedPolicyReadsTheWholeBody() throws IOException {
		final var response = new BasicClassicHttpResponse(200);
		response.setEntity(new StringEntity("a".repeat(LIMIT * 50), ContentType.TEXT_XML));
		final var loggedResponse = new BoundedLogbookHttpResponseInterceptor.LoggedResponse(response, new BodyCapturePolicy(-1));

		assertThat(loggedResponse.withBody().getBody()).hasSize(LIMIT * 50);
		assertThat(response.getEntity().isRepeatable()).isTrue();
	}

	@Test
	void responseLoggedWithoutBodyIsNotRead() throws IOException {
		final var response = new BasicClassicHttpResponse(200);
		final var entity = new StringEntity("body", ContentType.TEXT_XML);
		response.setEntity(entity);
		final var loggedResponse = new BoundedLogbookHttpResponseInterceptor.LoggedResponse(response, policy);

		assertThat(loggedResponse.withBody().withoutBody().getBody()).isEmpty();
		assertThat(response.getEntity()).isSameAs(entity);
	}

	@Test
	void responseWithoutEntityHasNoBody() throws IOException {
		assertThat(new BoundedLogbookHttpResponseInterceptor.LoggedResponse(new BasicClassicHttpResponse(204), policy).withBody().getBody()).isEmpty();
		assertThat(new BoundedLogbookHttpResponseInterceptor.LoggedResponse(new BasicHttpResponse(204), policy).withBody().getBody()).isEmpty();
	}

	@Test
	void describesTheResponse() {
		final var response = new BasicClassicHttpResponse(201);
		response.setVersion(HttpVersion.HTTP_2);
		response.addHeader("Content-Type", "text/xml; charset=ISO-8859-1");
		response.addHeader("X-Multi", "one");
		response.addHeader("X-Multi", "two");
		final var loggedResponse = new BoundedLogbookHttpResponseInterceptor.LoggedResponse(response, policy);

		assertThat(loggedResponse.getStatus()).isEqualTo(201);
		assertThat(loggedResponse.getProtocolVersion()).isEqualTo("HTTP/2.0");
		assertThat(loggedResponse.getOrigin()).isEqualTo(Origin.REMOTE);
		assertThat(loggedResponse.getHeaders()).contains(entry("Content-Type", List.of("text/xml; charset=ISO-8859-1")), entry("X-Multi", List.of("one", "two")));
		assertThat(loggedResponse.getContentType()).isEqualTo("text/xml; charset=ISO-8859-1");
		assertThat(loggedResponse.getCharset()).isEqualTo(ISO_8859_1);
	}

	@Test
	void responseWithoutContentTypeIsReadAsUtf8() {
		final var loggedResponse = new BoundedLogbookHttpResponseInterceptor.LoggedResponse(new BasicClassicHttpResponse(200), policy);

		assertThat(loggedResponse.getContentType()).isNull();
		assertThat(loggedResponse.getCharset()).isEqualTo(UTF_8);
	}

	@Test
	void failureToLogDoesNotFailTheResponse() throws IOException {
		final var stage = mock(ResponseProcessingStage.class);
		when(stage.process(any())).thenThrow(new IOException("sink down"));
		final var context = new BasicHttpContext();
		context.setAttribute(BoundedLogbookHttpResponseInterceptor.STAGE, stage);
		final var interceptor = new BoundedLogbookHttpResponseInterceptor(policy);

		assertThatNoException().isThrownBy(() -> interceptor.process(new BasicClassicHttpResponse(200), null, context));
	}

	@Test
	void requestThatWasNotLoggedIsLeftAlone() {
		final var response = new BasicClassicHttpResponse(200);
		final var entity = new StringEntity("body", ContentType.TEXT_XML);
		response.setEntity(entity);

		new BoundedLogbookHttpResponseInterceptor(policy).process(response, null, new BasicHttpContext());

		assertThat(response.getEntity()).isSameAs(entity);
	}

	private Received call(final int size) throws IOException {
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

		try (final var client = HttpClients.custom()
			.addRequestInterceptorFirst(new LogbookHttpRequestInterceptor(logbook))
			.addResponseInterceptorFirst(new BoundedLogbookHttpResponseInterceptor(policy))
			.build()) {
			return client.execute(new HttpGet("http://localhost:" + server.getAddress().getPort() + "/?" + size),
				response -> new Received(EntityUtils.toString(response.getEntity(), UTF_8), response.getEntity().isRepeatable()));
		}
	}

	private record Received(String body, boolean repeatable) {
	}
}
