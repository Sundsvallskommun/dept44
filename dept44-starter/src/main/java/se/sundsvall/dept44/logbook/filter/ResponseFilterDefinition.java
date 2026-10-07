package se.sundsvall.dept44.logbook.filter;

import org.zalando.logbook.ResponseFilter;

import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.zalando.logbook.core.ResponseFilters.replaceBody;
import static se.sundsvall.dept44.logbook.BodyCapturePolicy.isTextual;

public class ResponseFilterDefinition {

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

			if (isNotEmpty(contentTypes) && !isTextual(contentTypes.getFirst())) {
				return "<binary>";
			}

			return null;
		});
	}
}
