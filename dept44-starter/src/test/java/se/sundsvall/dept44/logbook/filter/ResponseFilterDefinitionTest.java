package se.sundsvall.dept44.logbook.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_XML_VALUE;
import static org.springframework.http.MediaType.IMAGE_GIF_VALUE;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.http.MediaType.TEXT_XML_VALUE;
import static org.zalando.logbook.ContentType.CONTENT_TYPE_HEADER;
import static org.zalando.logbook.HttpHeaders.of;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.binaryContentFilter;
import static se.sundsvall.dept44.logbook.filter.ResponseFilterDefinition.fileAttachmentFilter;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.test.MockHttpResponse;

class ResponseFilterDefinitionTest {

	@Test
	void fileAttachmentFilterReplace() throws IOException {
		final var filter = fileAttachmentFilter();
		final var response = filter.filter(MockHttpResponse.create()
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
		final var filter = fileAttachmentFilter();
		final var response = filter.filter(MockHttpResponse.create()
			.withContentType("text/plain")
			.withBodyAsString("do not filter me"));

		response.withBody();

		assertThat(response.getContentType()).isEqualTo("text/plain");
		assertThat(response.getBodyAsString()).isEqualTo("do not filter me");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		APPLICATION_PDF_VALUE, APPLICATION_OCTET_STREAM_VALUE, IMAGE_PNG_VALUE, IMAGE_JPEG_VALUE, IMAGE_GIF_VALUE
	})
	void binaryContentFilterReplace(String contentType) throws IOException {
		final var filter = binaryContentFilter();
		final var response = filter.filter(MockHttpResponse.create()
			.withHeaders(HttpHeaders.of(CONTENT_TYPE_HEADER, contentType))
			.withBodyAsString("this is binary data!"));

		response.withBody();

		assertThat(response.getBodyAsString()).isEqualTo("<binary>");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"text/*", ALL_VALUE, TEXT_HTML_VALUE, TEXT_XML_VALUE, TEXT_PLAIN_VALUE, APPLICATION_PROBLEM_JSON_VALUE, APPLICATION_PROBLEM_XML_VALUE, APPLICATION_GRAPHQL_RESPONSE_VALUE
	})
	void binaryContentDoNotReplace(String contentType) throws IOException {
		final var filter = binaryContentFilter();
		final var response = filter.filter(MockHttpResponse.create()
			.withHeaders(HttpHeaders.of(CONTENT_TYPE_HEADER, contentType))
			.withBodyAsString("do not filter me"));

		response.withBody();

		assertThat(response.getBodyAsString()).isEqualTo("do not filter me");
	}
}
