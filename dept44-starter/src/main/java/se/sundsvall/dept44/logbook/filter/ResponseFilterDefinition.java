package se.sundsvall.dept44.logbook.filter;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.zalando.logbook.core.ResponseFilters.replaceBody;

import org.zalando.logbook.ResponseFilter;

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
}
