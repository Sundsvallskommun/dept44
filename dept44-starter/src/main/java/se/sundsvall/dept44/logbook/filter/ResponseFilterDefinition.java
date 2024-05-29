package se.sundsvall.dept44.logbook.filter;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.zalando.logbook.core.ResponseFilters.replaceBody;

import java.util.List;

import org.springframework.http.MediaType;
import org.zalando.logbook.ResponseFilter;

public class ResponseFilterDefinition {

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

	private ResponseFilterDefinition() {}

	public static ResponseFilter fileAttachmentFilter() {
		return replaceBody(response -> {
			final var contentDisposition = response.getHeaders().get(CONTENT_DISPOSITION);

			if (nonNull(contentDisposition)) {
				final var isFile = contentDisposition.stream()
					.anyMatch(value -> value.contains("attachment; filename="));
				if (isFile) {
					return "<binary>";
				}
			}

			return null;
		});
	}

	public static ResponseFilter binaryContentFilter() {
		return replaceBody(response -> {
			final var contentTypes = response.getHeaders().get(CONTENT_TYPE);

			if (isNotEmpty(contentTypes)) {
				try {
					final var contentType = MediaType.valueOf(contentTypes.getFirst());
					final var isText = TEXT_MEDIA_TYPES.stream()
						.anyMatch(textMediaType -> textMediaType.isCompatibleWith(contentType));

					if (!isText) {
						return "<binary>";
					}
				} catch (final Exception e) {
					// Do nothing
				}
			}

			return null;
		});
	}
}
