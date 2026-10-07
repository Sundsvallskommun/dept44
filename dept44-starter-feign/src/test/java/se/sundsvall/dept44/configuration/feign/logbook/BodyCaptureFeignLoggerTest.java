package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Logger.Level;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.RequestFilter;
import org.zalando.logbook.ResponseFilter;
import org.zalando.logbook.Sink;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.logbook.BodyCaptureStrategy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

class BodyCaptureFeignLoggerTest {

	private static final int LIMIT = 100;
	private static final String CONFIG_KEY = "Client#method()";

	private final CapturingSink sink = new CapturingSink();
	private final BodyCaptureFeignLogger logger = logger(new BodyCapturePolicy(LIMIT));

	@Test
	void smallResponseIsLoggedAndRebuffered() throws IOException {
		final var body = new TrackingInputStream(bytes(50));
		final var response = response(200, "application/json", body, 50);

		final var result = logResponse(response);

		assertThat(sink.responseBody).isEqualTo(text(50));
		assertThat(body.closed).isTrue();
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(50));
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(50));
	}

	@Test
	void largeResponseOfKnownLengthIsHandedOnUntouched() throws IOException {
		final var body = new TrackingInputStream(bytes(LIMIT + 1));
		final var response = response(200, "application/json", body, LIMIT + 1);

		final var result = logResponse(response);

		assertThat(result).isSameAs(response);
		assertThat(body.bytesRead).isZero();
		assertThat(sink.responseBody).isEmpty();
	}

	@Test
	void largeResponseOfUnknownLengthIsStreamed() throws IOException {
		final var body = new TrackingInputStream(bytes(LIMIT * 3));
		final var response = response(200, "application/json", body, null);

		final var result = logResponse(response);

		assertThat(body.bytesRead).isEqualTo(LIMIT + 1);
		assertThat(sink.responseBody).isEmpty();
		assertThat(result.body().length()).isNull();
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
		result.close();
		assertThat(body.closed).isTrue();
	}

	@Test
	void smallResponseOfUnknownLengthIsLoggedAndRebuffered() throws IOException {
		final var body = new TrackingInputStream(bytes(LIMIT));
		final var response = response(200, "application/json", body, null);

		final var result = logResponse(response);

		assertThat(sink.responseBody).isEqualTo(text(LIMIT));
		assertThat(body.closed).isTrue();
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT));
	}

	@Test
	void binaryResponseIsHandedOnUntouched() throws IOException {
		final var body = new TrackingInputStream(bytes(10));
		final var response = response(200, "application/pdf", body, 10);

		final var result = logResponse(response);

		assertThat(result).isSameAs(response);
		assertThat(body.bytesRead).isZero();
		assertThat(sink.responseBody).isEmpty();
	}

	@Test
	void attachmentResponseIsHandedOnUntouched() throws IOException {
		final var body = new TrackingInputStream(bytes(10));
		final var response = Response.builder()
			.status(200)
			.request(request(null))
			.headers(Map.of(CONTENT_TYPE, List.of("text/csv"), CONTENT_DISPOSITION, List.of("attachment; filename=\"a.csv\"")))
			.body(body, 10)
			.build();

		final var result = logResponse(response);

		assertThat(result).isSameAs(response);
		assertThat(body.bytesRead).isZero();
	}

	@Test
	void smallErrorResponseIsKeptInMemory() throws IOException {
		final var body = new TrackingInputStream(bytes(50));
		final var response = response(500, "application/problem+json", body, null);

		final var result = logResponse(response);

		assertThat(body.closed).isTrue();
		assertThat(result.body().isRepeatable()).isTrue();
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(50));
		assertThat(sink.responseBody).isEqualTo(text(50));
	}

	@Test
	void largeErrorResponseIsKeptInTemporaryFileUntilClosed(@TempDir final Path directory) throws IOException {
		final var fileLogger = logger(new BodyCapturePolicy(LIMIT), directory);
		final var body = new TrackingInputStream(bytes(LIMIT * 3));
		final var response = response(500, "application/problem+json", body, null);
		fileLogger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		final var result = fileLogger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);

		assertThat(body.closed).isTrue();
		assertThat(sink.responseBody).isEmpty();
		assertThat(result.body().isRepeatable()).isTrue();
		assertThat(result.body().length()).isEqualTo(LIMIT * 3);
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
		assertThat(directory).isNotEmptyDirectory();

		result.close();

		assertThat(directory).isEmptyDirectory();
	}

	@Test
	void largeErrorResponseIsStreamedWhenNoTemporaryFileCanBeCreated(@TempDir final Path directory) throws IOException {
		final var fileLogger = logger(new BodyCapturePolicy(LIMIT), directory.resolve("missing"));
		final var response = response(500, "application/problem+json", new TrackingInputStream(bytes(LIMIT * 3)), null);
		fileLogger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		final var result = fileLogger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);

		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
	}

	@Test
	void withoutLimitErrorResponseIsRebufferedInFull() throws IOException {
		final var unlimitedLogger = logger(new BodyCapturePolicy(-1));
		final var response = response(500, "application/problem+json", new TrackingInputStream(bytes(LIMIT * 3)), null);
		unlimitedLogger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		final var result = unlimitedLogger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);

		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
		assertThat(sink.responseBody).isEqualTo(text(LIMIT * 3));
	}

	@Test
	void responseWithoutBodyIsLogged() throws IOException {
		final var response = Response.builder()
			.status(204)
			.request(request(null))
			.headers(Map.of())
			.build();

		final var result = logResponse(response);

		assertThat(result).isSameAs(response);
		assertThat(sink.responseWritten).isTrue();
	}

	@Test
	void responseWithoutLoggedRequestIsHandedOnUntouched() throws IOException {
		final var response = response(200, "application/json", new TrackingInputStream(bytes(10)), 10);

		final var result = logger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);

		assertThat(result).isSameAs(response);
		assertThat(sink.responseWritten).isFalse();
	}

	@Test
	void ioExceptionForgetsTheRequest() throws IOException {
		final var exception = new IOException("connection reset");
		logger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		assertThat(logger.logIOException(CONFIG_KEY, Level.FULL, exception, 1)).isSameAs(exception);

		final var response = response(200, "application/json", new TrackingInputStream(bytes(10)), 10);
		assertThat(logger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1)).isSameAs(response);
		assertThat(sink.responseWritten).isFalse();
	}

	@Test
	void failedReadClosesTheBody() {
		final var body = new TrackingInputStream(bytes(10)) {
			@Override
			public int read(final byte[] buffer, final int offset, final int length) throws IOException {
				throw new IOException("read failed");
			}
		};
		final var response = response(200, "application/json", body, null);
		logger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		assertThatThrownBy(() -> logger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1)).isInstanceOf(IOException.class);
		assertThat(body.closed).isTrue();
	}

	@Test
	void withoutLimitEveryResponseIsRebuffered() throws IOException {
		final var unlimitedLogger = logger(new BodyCapturePolicy(-1));
		final var body = new TrackingInputStream(bytes(LIMIT * 3));
		final var response = response(200, "application/json", body, null);
		unlimitedLogger.logRequest(CONFIG_KEY, Level.FULL, request(null));

		final var result = unlimitedLogger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);

		assertThat(sink.responseBody).isEqualTo(text(LIMIT * 3));
		assertThat(result.body().asInputStream().readAllBytes()).isEqualTo(bytes(LIMIT * 3));
	}

	@Test
	void smallRequestBodyIsLogged() {
		logger.logRequest(CONFIG_KEY, Level.FULL, request(bytes(50)));

		assertThat(sink.requestBody).isEqualTo(text(50));
	}

	@Test
	void largeRequestBodyIsNotLogged() {
		logger.logRequest(CONFIG_KEY, Level.FULL, request(bytes(LIMIT + 1)));

		assertThat(sink.requestBody).isEmpty();
	}

	@Test
	void logAndRetryDoNothing() {
		logger.log(CONFIG_KEY, "format %s", "argument");
		logger.logRetry(CONFIG_KEY, Level.FULL);

		assertThat(sink.requestBody).isNull();
		assertThat(sink.responseWritten).isFalse();
	}

	private Response logResponse(final Response response) throws IOException {
		logger.logRequest(CONFIG_KEY, Level.FULL, request(null));
		return logger.logAndRebufferResponse(CONFIG_KEY, Level.FULL, response, 1);
	}

	private BodyCaptureFeignLogger logger(final BodyCapturePolicy policy) {
		return logger(policy, null);
	}

	private BodyCaptureFeignLogger logger(final BodyCapturePolicy policy, final Path temporaryDirectory) {
		return new BodyCaptureFeignLogger(Logbook.builder()
			.strategy(new BodyCaptureStrategy(policy))
			.requestFilter(RequestFilter.none())
			.responseFilter(ResponseFilter.none())
			.sink(sink)
			.build(), policy, temporaryDirectory);
	}

	private static Request request(final byte[] body) {
		final Map<String, Collection<String>> headers = Map.of(CONTENT_TYPE, List.of("application/json"));
		return Request.create(HttpMethod.POST, "http://localhost:8080/path", headers, body, UTF_8, null);
	}

	private static Response response(final int status, final String contentType, final InputStream body, final Integer length) {
		return Response.builder()
			.status(status)
			.request(request(null))
			.headers(Map.of(CONTENT_TYPE, List.of(contentType)))
			.body(body, length)
			.build();
	}

	private static byte[] bytes(final int size) {
		final var bytes = new byte[size];
		Arrays.fill(bytes, (byte) 'a');
		return bytes;
	}

	private static String text(final int size) {
		return new String(bytes(size), UTF_8);
	}

	private static class TrackingInputStream extends FilterInputStream {

		long bytesRead;
		boolean closed;

		TrackingInputStream(final byte[] content) {
			super(new ByteArrayInputStream(content));
		}

		@Override
		public int read() throws IOException {
			final var result = super.read();
			if (result != -1) {
				bytesRead++;
			}
			return result;
		}

		@Override
		public int read(final byte[] buffer, final int offset, final int length) throws IOException {
			final var result = super.read(buffer, offset, length);
			if (result > 0) {
				bytesRead += result;
			}
			return result;
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

	private static final class CapturingSink implements Sink {

		private String requestBody;
		private String responseBody;
		private boolean responseWritten;

		@Override
		public void write(final Precorrelation precorrelation, final HttpRequest request) throws IOException {
			requestBody = request.getBodyAsString();
		}

		@Override
		public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) throws IOException {
			responseBody = response.getBodyAsString();
			responseWritten = true;
		}
	}
}
