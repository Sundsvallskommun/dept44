package se.sundsvall.dept44.logbook.filter;

import org.zalando.logbook.ResponseFilter;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.zalando.logbook.core.ResponseFilters.*;

public class ResponseFilterDefinition {

	public static ResponseFilter fileAttachmentFilter() {
		return replaceBody(response -> {
			var contentDisposition = response.getHeaders().get(CONTENT_DISPOSITION);
			if (nonNull(contentDisposition)) {
				var isFile = contentDisposition.stream()
					.anyMatch(value -> value.contains("attachment; filename="));
				if (isFile) {
					return "<binary>";
				}
			}
			return null;
		});
	}
}
