package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.lang.Boolean.TRUE;
import static org.springframework.http.HttpHeaders.TRANSFER_ENCODING;
import static se.sundsvall.dept44.logbook.BodyCaptureStrategy.SKIP_BODY_CAPTURE_ATTRIBUTE;

/**
 * Finds out whether a request body of unknown length (chunked, or HTTP/2 without Content-Length) is larger than the
 * {@link BodyCapturePolicy} allows, without holding more than the allowed size in memory.
 * <p>
 * At most the allowed size plus one byte is read. If the body turns out to be larger, the request is marked so that
 * payload logging leaves its body alone. Either way the request is handed on with the bytes already read put back in
 * front of the rest of the body.
 */
final class UnknownLengthRequestInspector {

	private static final MediaType MULTIPART = MediaType.valueOf("multipart/*");

	private UnknownLengthRequestInspector() {}

	static HttpServletRequest inspect(final HttpServletRequest request, final BodyCapturePolicy policy) throws IOException {
		if (!policy.isLimited() || !hasBodyOfUnknownLength(request)) {
			return request;
		}

		final var contentType = request.getContentType();
		if (contentType == null) {
			return measured(request, policy);
		}

		final MediaType mediaType;
		try {
			mediaType = MediaType.valueOf(contentType);
		} catch (final InvalidMediaTypeException _) {
			// Payload logging would capture this body, but it cannot be measured safely: the container may still parse it
			// as a form from the raw stream, which must not have been read before. So it is neither read nor captured.
			request.setAttribute(SKIP_BODY_CAPTURE_ATTRIBUTE, TRUE);
			return request;
		}

		if (!BodyCapturePolicy.isTextual(contentType) || isParsedByContainer(mediaType)) {
			return request;
		}
		return measured(request, policy);
	}

	private static HttpServletRequest measured(final HttpServletRequest request, final BodyCapturePolicy policy) throws IOException {
		final var body = request.getInputStream();
		final var head = body.readNBytes(policy.readLimit());
		if (policy.exceedsLimit(head.length)) {
			request.setAttribute(SKIP_BODY_CAPTURE_ATTRIBUTE, TRUE);
		}

		return new BodyReplayingRequestWrapper(request, new SequenceInputStream(new ByteArrayInputStream(head), body));
	}

	private static boolean hasBodyOfUnknownLength(final HttpServletRequest request) {
		return request.getContentLengthLong() < 0
			&& (request.getHeader(TRANSFER_ENCODING) != null || request.getProtocol().startsWith("HTTP/2"));
	}

	/**
	 * Form and multipart bodies are left alone: the servlet container parses them from the raw stream, which must not
	 * have been read before.
	 */
	private static boolean isParsedByContainer(final MediaType mediaType) {
		return MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType) || MULTIPART.isCompatibleWith(mediaType);
	}
}
