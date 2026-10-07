package se.sundsvall.dept44.logbook;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.test.MockHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

class BodyCapturePolicyTest {

	private final BodyCapturePolicy policy = new BodyCapturePolicy(100);

	@ParameterizedTest
	@ValueSource(strings = {
		"application/json", "application/json; charset=UTF-8", "application/problem+json", "text/plain", "text/csv", "application/xml",
		"application/yaml", "multipart/form-data; boundary=abc", "application/x-www-form-urlencoded", "not a media type"
	})
	@NullAndEmptySource
	void isTextual(final String contentType) {
		assertThat(BodyCapturePolicy.isTextual(contentType)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/pdf", "application/octet-stream", "image/png", "application/zip"
	})
	void isNotTextual(final String contentType) {
		assertThat(BodyCapturePolicy.isTextual(contentType)).isFalse();
	}

	@ParameterizedTest
	@MethodSource("attachmentArguments")
	void isAttachment(final List<String> contentDisposition, final boolean expected) {
		assertThat(BodyCapturePolicy.isAttachment(contentDisposition)).isEqualTo(expected);
	}

	private static Stream<Arguments> attachmentArguments() {
		return Stream.of(
			Arguments.of(null, false),
			Arguments.of(List.of(), false),
			Arguments.of(List.of("inline"), false),
			Arguments.of(List.of("attachment"), true),
			Arguments.of(List.of("attachment; filename=\"a.pdf\""), true),
			Arguments.of(List.of(" Attachment; filename=a.pdf"), true));
	}

	@ParameterizedTest
	@MethodSource("lengthArguments")
	void parseLength(final String value, final long expected) {
		assertThat(BodyCapturePolicy.parseLength(value)).isEqualTo(expected);
	}

	private static Stream<Arguments> lengthArguments() {
		return Stream.of(
			Arguments.of(null, -1L),
			Arguments.of("abc", -1L),
			Arguments.of("42", 42L),
			Arguments.of(" 42 ", 42L));
	}

	@Test
	void limit() {
		assertThat(policy.isLimited()).isTrue();
		assertThat(policy.getMaxBodySize()).isEqualTo(100);
		assertThat(policy.exceedsLimit(100)).isFalse();
		assertThat(policy.exceedsLimit(101)).isTrue();
		assertThat(policy.exceedsLimit(-1)).isFalse();
	}

	@Test
	void readLimit() {
		assertThat(policy.readLimit()).isEqualTo(101);
		assertThat(new BodyCapturePolicy(Long.MAX_VALUE - 1).readLimit()).isEqualTo(Integer.MAX_VALUE - 8);
	}

	@Test
	void noLimit() {
		final var unlimited = new BodyCapturePolicy(-1);

		assertThat(unlimited.isLimited()).isFalse();
		assertThat(unlimited.exceedsLimit(Long.MAX_VALUE)).isFalse();
	}

	@ParameterizedTest
	@MethodSource("captureArguments")
	void allowsCapture(final String contentType, final List<String> contentDisposition, final long contentLength, final boolean expected) {
		assertThat(policy.allowsCapture(contentType, contentDisposition, contentLength)).isEqualTo(expected);
	}

	private static Stream<Arguments> captureArguments() {
		return Stream.of(
			Arguments.of("application/json", null, 10L, true),
			Arguments.of("application/json", null, -1L, true),
			Arguments.of(null, null, -1L, true),
			Arguments.of("application/json", null, 101L, false),
			Arguments.of("application/pdf", null, 10L, false),
			Arguments.of("application/json", List.of("attachment; filename=a.json"), 10L, false));
	}

	@Test
	void allowsCaptureFromMessageHeaders() {
		final var small = MockHttpResponse.create()
			.withContentType("application/json")
			.withHeaders(HttpHeaders.of(CONTENT_LENGTH, "10"));
		final var large = MockHttpResponse.create()
			.withContentType("application/json")
			.withHeaders(HttpHeaders.of(CONTENT_LENGTH, "101"));
		final var attachment = MockHttpResponse.create()
			.withContentType("text/csv")
			.withHeaders(HttpHeaders.of(CONTENT_DISPOSITION, "attachment; filename=a.csv"));

		assertThat(policy.allowsCapture(small)).isTrue();
		assertThat(policy.allowsCapture(large)).isFalse();
		assertThat(policy.allowsCapture(attachment)).isFalse();
	}
}
