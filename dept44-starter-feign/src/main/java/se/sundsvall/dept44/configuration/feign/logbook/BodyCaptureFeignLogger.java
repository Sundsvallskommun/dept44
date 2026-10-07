package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Logger;
import feign.Request;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.ResponseProcessingStage;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static feign.Util.ensureClosed;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * Feign logger that hands requests and responses to Logbook, like Logbook's own {@code FeignLogbookLogger}, without
 * reading response bodies into memory that payload logging will not capture.
 * <p>
 * Logbook's logger reads every response body into a byte array before the response is decoded, so a file fetched
 * through Feign is held in memory once more than the client itself needs, and a client returning {@link Response}
 * cannot stream. This logger reads the body only for error responses, which error decoders may read more than once, and
 * for textual bodies within the size that the {@link BodyCapturePolicy} allows. Any other response is logged without
 * its body and handed on with its body untouched.
 * <p>
 * An error body larger than the limit is kept in a temporary file rather than in memory, since error decoders may read
 * it more than once.
 */
public class BodyCaptureFeignLogger extends Logger {

	private final Logbook logbook;
	private final BodyCapturePolicy policy;
	private final Path temporaryDirectory;

	// Feign is blocking, so a request and its response are handled on the same thread
	private final ThreadLocal<ResponseProcessingStage> stage = new ThreadLocal<>();

	public BodyCaptureFeignLogger(final Logbook logbook, final BodyCapturePolicy policy) {
		this(logbook, policy, null);
	}

	/**
	 * @param temporaryDirectory where oversized error bodies are kept; {@code null} means the default temporary directory
	 */
	BodyCaptureFeignLogger(final Logbook logbook, final BodyCapturePolicy policy, final Path temporaryDirectory) {
		this.logbook = logbook;
		this.policy = policy;
		this.temporaryDirectory = temporaryDirectory;
	}

	@Override
	protected void log(final String configKey, final String format, final Object... args) {
		// Logging is delegated to Logbook
	}

	@Override
	protected void logRetry(final String configKey, final Level logLevel) {
		// Logging is delegated to Logbook
	}

	@Override
	protected IOException logIOException(final String configKey, final Level logLevel, final IOException ioe, final long elapsedTime) {
		stage.remove();
		return ioe;
	}

	@Override
	protected void logRequest(final String configKey, final Level logLevel, final Request request) {
		try {
			stage.set(logbook.process(FeignLogbookRequest.create(request)).write());
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	protected Response logAndRebufferResponse(final String configKey, final Level logLevel, final Response response, final long elapsedTime) throws IOException {
		final var processingStage = stage.get();
		stage.remove();

		if (processingStage == null) {
			return response;
		}
		if (response.body() == null) {
			write(processingStage, response, null);
			return response;
		}
		if (isError(response)) {
			return rebufferError(processingStage, response);
		}
		if (!policy.allowsCapture(contentType(response), response.headers().get(CONTENT_DISPOSITION), length(response))) {
			write(processingStage, response, null);
			return response;
		}
		return rebufferWithinLimit(processingStage, response);
	}

	private Response rebuffer(final ResponseProcessingStage processingStage, final Response response) throws IOException {
		final byte[] body;
		try (final var input = response.body().asInputStream()) {
			body = input.readAllBytes();
		} finally {
			ensureClosed(response.body());
		}

		write(processingStage, response, body);
		return response.toBuilder().body(body).build();
	}

	/**
	 * Reads at most the allowed size plus one byte. A body that fits is logged and handed on as a byte array. A body
	 * that turns out to be larger is logged without its body and handed on as a stream, starting with the bytes already
	 * read.
	 */
	private Response rebufferWithinLimit(final ResponseProcessingStage processingStage, final Response response) throws IOException {
		if (!policy.isLimited()) {
			return rebuffer(processingStage, response);
		}

		final var input = response.body().asInputStream();
		final var head = readHead(input, response);
		if (!policy.exceedsLimit(head.length)) {
			return withBuffer(processingStage, response, input, head);
		}

		write(processingStage, response, null);
		return streaming(response, input, head);
	}

	/**
	 * Like {@link #rebufferWithinLimit}, except that a body larger than the limit is kept in a temporary file, so that
	 * error decoders can still read it more than once. If no temporary file can be created, the body is handed on as a
	 * stream.
	 */
	private Response rebufferError(final ResponseProcessingStage processingStage, final Response response) throws IOException {
		if (!policy.isLimited()) {
			return rebuffer(processingStage, response);
		}

		final var input = response.body().asInputStream();
		final var head = readHead(input, response);
		if (!policy.exceedsLimit(head.length)) {
			return withBuffer(processingStage, response, input, head);
		}

		write(processingStage, response, null);
		final Path file;
		try {
			file = TempFileBody.createFile(temporaryDirectory);
		} catch (final IOException _) {
			return streaming(response, input, head);
		}
		try {
			return response.toBuilder().body(TempFileBody.fill(file, head, input)).build();
		} finally {
			ensureClosed(input);
			ensureClosed(response.body());
		}
	}

	private byte[] readHead(final InputStream input, final Response response) throws IOException {
		try {
			return input.readNBytes(policy.readLimit());
		} catch (final IOException | RuntimeException e) {
			ensureClosed(input);
			ensureClosed(response.body());
			throw e;
		}
	}

	private static Response withBuffer(final ResponseProcessingStage processingStage, final Response response, final InputStream input, final byte[] body) throws IOException {
		ensureClosed(input);
		ensureClosed(response.body());
		write(processingStage, response, body);
		return response.toBuilder().body(body).build();
	}

	private static Response streaming(final Response response, final InputStream input, final byte[] head) {
		return response.toBuilder()
			.body(new SequenceInputStream(new ByteArrayInputStream(head), input), response.body().length())
			.build();
	}

	private static void write(final ResponseProcessingStage processingStage, final Response response, final byte[] body) throws IOException {
		processingStage.process(FeignLogbookResponse.create(response, body)).write();
	}

	private static boolean isError(final Response response) {
		return response.status() < 200 || response.status() >= 300;
	}

	private static String contentType(final Response response) {
		return Optional.ofNullable(response.headers().get(CONTENT_TYPE)).stream()
			.flatMap(Collection::stream)
			.findFirst()
			.orElse(null);
	}

	private static long length(final Response response) {
		return Optional.ofNullable(response.body().length())
			.map(Integer::longValue)
			.orElse(-1L);
	}
}
