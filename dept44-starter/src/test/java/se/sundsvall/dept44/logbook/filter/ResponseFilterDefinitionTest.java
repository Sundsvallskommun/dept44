package se.sundsvall.dept44.logbook.filter;

import org.junit.jupiter.api.Test;
import org.zalando.logbook.test.MockHttpResponse;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.zalando.logbook.HttpHeaders.of;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.fileAttachmentFilter;

class ResponseFilterDefinitionTest {

	@Test
	void fileAttachmentFilterReplace() throws IOException {
		var filter = fileAttachmentFilter();
		var response = filter.filter(MockHttpResponse.create()
			.withHeaders(of(CONTENT_DISPOSITION, "attachment; filename=test.zip"))
				.withContentType("application/x-rar-compressed")
			.withBodyAsString("this is binary data!"));

		response.withBody();

		assertThat(response.getContentType()).isEqualTo("application/x-rar-compressed");
		assertThat(response.getHeaders()).containsEntry(CONTENT_DISPOSITION, Arrays.asList("attachment; filename=test.zip"));
		assertThat(response.getBodyAsString()).isEqualTo("<binary>");
	}

	@Test
	void fileAttachmentFilterDoNotReplace() throws IOException {
		var filter = fileAttachmentFilter();
		var response = filter.filter(MockHttpResponse.create()
			.withContentType("text/plain")
			.withBodyAsString("do not filter me"));

		response.withBody();

		assertThat(response.getContentType()).isEqualTo("text/plain");
		assertThat(response.getBodyAsString()).isEqualTo("do not filter me");
	}
}
