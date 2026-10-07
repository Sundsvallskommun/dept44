package se.sundsvall.dept44.logbook.servlet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.TRANSFER_ENCODING;
import static se.sundsvall.dept44.logbook.BodyCaptureStrategy.OVERSIZED_REQUEST_ATTRIBUTE;

class UnknownLengthRequestInspectorTest {

	private final BodyCapturePolicy policy = new BodyCapturePolicy(5);

	@Test
	void requestWithKnownLengthIsLeftAlone() throws Exception {
		final var request = new MockHttpServletRequest("POST", "/");
		request.setContentType("application/json");
		request.setContent("0123456789".getBytes(UTF_8));

		assertThat(UnknownLengthRequestInspector.inspect(request, policy)).isSameAs(request);
	}

	@Test
	void requestWithoutBodyIsLeftAlone() throws Exception {
		final var request = unknownLengthRequest("application/json", "");
		request.removeHeader(TRANSFER_ENCODING);

		assertThat(UnknownLengthRequestInspector.inspect(request, policy)).isSameAs(request);
	}

	@Test
	void unlimitedPolicyLeavesRequestAlone() throws Exception {
		final var request = unknownLengthRequest("application/json", "0123456789");

		assertThat(UnknownLengthRequestInspector.inspect(request, new BodyCapturePolicy(-1))).isSameAs(request);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/x-www-form-urlencoded", "multipart/form-data; boundary=abc", "application/pdf", "not a media type"
	})
	void bodiesThatAreNotCapturedAreLeftAlone(final String contentType) throws Exception {
		final var request = unknownLengthRequest(contentType, "0123456789");

		assertThat(UnknownLengthRequestInspector.inspect(request, policy)).isSameAs(request);
	}

	@Test
	void oversizedBodyIsMarkedAndReplayed() throws Exception {
		final var request = unknownLengthRequest("application/json", "0123456789");

		final var inspected = UnknownLengthRequestInspector.inspect(request, policy);

		assertThat(inspected).isNotSameAs(request);
		assertThat(inspected.getInputStream().readAllBytes()).isEqualTo("0123456789".getBytes(UTF_8));
		assertThat(request.getAttribute(OVERSIZED_REQUEST_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
	}

	@Test
	void bodyWithinLimitIsReplayedWithoutMark() throws Exception {
		final var request = unknownLengthRequest(null, "01234");

		final var inspected = UnknownLengthRequestInspector.inspect(request, policy);

		assertThat(inspected.getInputStream().readAllBytes()).isEqualTo("01234".getBytes(UTF_8));
		assertThat(request.getAttribute(OVERSIZED_REQUEST_ATTRIBUTE)).isNull();
	}

	@Test
	void http2RequestWithoutLengthIsInspected() throws Exception {
		final var request = unknownLengthRequest("application/json", "0123456789");
		request.removeHeader(TRANSFER_ENCODING);
		request.setProtocol("HTTP/2.0");

		UnknownLengthRequestInspector.inspect(request, policy);

		assertThat(request.getAttribute(OVERSIZED_REQUEST_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
	}

	private static MockHttpServletRequest unknownLengthRequest(final String contentType, final String content) {
		final var request = new MockHttpServletRequest("POST", "/") {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContentType(contentType);
		request.setContent(content.getBytes(UTF_8));
		request.addHeader(TRANSFER_ENCODING, "chunked");
		return request;
	}
}
