package se.sundsvall.dept44.logbook;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.zalando.logbook.HttpMessage;

import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

/**
 * Decides whether the body of a request or response may be captured (held in memory) for payload logging.
 * <p>
 * A body is only captured when it is textual, is not a file attachment and does not exceed the configured maximum
 * size. Everything else is logged without its body, so payload logging never holds a file in memory.
 */
public final class BodyCapturePolicy {

	private static final List<MediaType> TEXT_MEDIA_TYPES = List.of(
		MediaType.valueOf("application/yaml"),
		MediaType.valueOf("text/*"),
		MediaType.MULTIPART_FORM_DATA,
		MediaType.APPLICATION_ATOM_XML,
		MediaType.APPLICATION_RSS_XML,
		MediaType.APPLICATION_XHTML_XML,
		MediaType.APPLICATION_XML,
		MediaType.APPLICATION_NDJSON,
		MediaType.APPLICATION_JSON,
		MediaType.APPLICATION_PROBLEM_JSON,
		MediaType.APPLICATION_PROBLEM_XML,
		MediaType.APPLICATION_GRAPHQL_RESPONSE,
		MediaType.APPLICATION_FORM_URLENCODED);

	private static final String ATTACHMENT = "attachment";
	private static final long UNKNOWN_LENGTH = -1;
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private final long maxBodySize;

	/**
	 * @param maxBodySize the largest body, in bytes, that may be captured. A negative value disables the size limit.
	 */
	public BodyCapturePolicy(final long maxBodySize) {
		this.maxBodySize = maxBodySize;
	}

	public long getMaxBodySize() {
		return maxBodySize;
	}

	public boolean isLimited() {
		return maxBodySize >= 0;
	}

	public boolean exceedsLimit(final long size) {
		return isLimited() && size > maxBodySize;
	}

	/**
	 * The number of bytes to read from a body of unknown length to find out whether it exceeds the limit: the limit plus
	 * one, capped at the largest possible array. Only meaningful when {@link #isLimited()}.
	 */
	public int readLimit() {
		return toIntExact(min(maxBodySize + 1, MAX_ARRAY_SIZE));
	}

	/**
	 * Decides from the headers of a message whether its body may be captured. A message without a known length is
	 * allowed here, since its size cannot be judged until it is read.
	 */
	public boolean allowsCapture(final HttpMessage message) {
		final var headers = message.getHeaders();
		return allowsCapture(message.getContentType(), headers.get(CONTENT_DISPOSITION), parseLength(headers.getFirst(CONTENT_LENGTH)));
	}

	public boolean allowsCapture(final String contentType, final Collection<String> contentDisposition, final long contentLength) {
		return isTextual(contentType) && !isAttachment(contentDisposition) && !exceedsLimit(contentLength);
	}

	/**
	 * A missing or unparsable content type counts as textual, so that only bodies known to be binary are treated as
	 * binary.
	 */
	public static boolean isTextual(final String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return true;
		}
		try {
			final var mediaType = MediaType.valueOf(contentType);
			return TEXT_MEDIA_TYPES.stream().anyMatch(textMediaType -> textMediaType.isCompatibleWith(mediaType));
		} catch (final Exception _) {
			return true;
		}
	}

	public static boolean isAttachment(final Collection<String> contentDisposition) {
		return Optional.ofNullable(contentDisposition).stream()
			.flatMap(Collection::stream)
			.anyMatch(value -> value.strip().toLowerCase(Locale.ROOT).startsWith(ATTACHMENT));
	}

	/**
	 * @return the parsed length, or -1 when the value is missing or not a number
	 */
	public static long parseLength(final String value) {
		if (value == null) {
			return UNKNOWN_LENGTH;
		}
		try {
			return Long.parseLong(value.strip());
		} catch (final NumberFormatException _) {
			return UNKNOWN_LENGTH;
		}
	}
}
