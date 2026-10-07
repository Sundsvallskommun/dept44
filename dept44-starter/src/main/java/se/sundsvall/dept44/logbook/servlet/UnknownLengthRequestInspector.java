package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import org.springframework.http.MediaType;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.lang.Boolean.TRUE;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static org.springframework.http.HttpHeaders.TRANSFER_ENCODING;
import static se.sundsvall.dept44.logbook.BodyCaptureStrategy.OVERSIZED_REQUEST_ATTRIBUTE;

/**
 * Finds out whether a request body of unknown length (chunked, or HTTP/2 without Content-Length) is larger than the
 * {@link BodyCapturePolicy} allows, without holding more than the allowed size in memory.
 * <p>
 * At most the allowed size plus one byte is read. If the body turns out to be larger, the request is marked so that
 * payload logging leaves its body alone. Either way the request is handed on with the bytes already read put back in
 * front of the rest of the body.
 */
final class UnknownLengthRequestInspector {

	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
	private static final MediaType MULTIPART = MediaType.valueOf("multipart/*");

	private UnknownLengthRequestInspector() {}

	static HttpServletRequest inspect(final HttpServletRequest request, final BodyCapturePolicy policy) throws IOException {
		if (!policy.isLimited() || !hasBodyOfUnknownLength(request) || !isInspectable(request.getContentType())) {
			return request;
		}

		final var body = request.getInputStream();
		final var head = body.readNBytes(toIntExact(min(policy.getMaxBodySize() + 1, MAX_ARRAY_SIZE)));
		if (policy.exceedsLimit(head.length)) {
			request.setAttribute(OVERSIZED_REQUEST_ATTRIBUTE, TRUE);
		}

		return new BodyReplayingRequestWrapper(request, new SequenceInputStream(new ByteArrayInputStream(head), body));
	}

	private static boolean hasBodyOfUnknownLength(final HttpServletRequest request) {
		return request.getContentLengthLong() < 0
			&& (request.getHeader(TRANSFER_ENCODING) != null || request.getProtocol().startsWith("HTTP/2"));
	}

	/**
	 * Only bodies that payload logging would capture are worth inspecting. Form and multipart bodies are left alone: the
	 * servlet container parses them from the raw stream, which must not have been read before.
	 */
	private static boolean isInspectable(final String contentType) {
		if (!BodyCapturePolicy.isTextual(contentType)) {
			return false;
		}
		if (contentType == null) {
			return true;
		}
		try {
			final var mediaType = MediaType.valueOf(contentType);
			return !MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType) && !MULTIPART.isCompatibleWith(mediaType);
		} catch (final Exception _) {
			return false;
		}
	}
}
