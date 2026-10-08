package se.sundsvall.dept44.logbook.servlet;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapturingRequestTest {

	private final BodyCapturePolicy policy = new BodyCapturePolicy(5);

	@Test
	void requestAttributes() {
		final var mock = new MockHttpServletRequest("PUT", "/a/b");
		mock.setRemoteAddr("10.0.0.1");
		mock.setServerName("host");
		mock.setServerPort(8443);
		mock.setQueryString("x=1");
		mock.setProtocol("HTTP/2.0");
		mock.addHeader("X-Test", "one");
		mock.addHeader("X-Test", "two");
		final var request = new CapturingRequest(mock, policy);

		assertThat(request.getRemote()).isEqualTo("10.0.0.1");
		assertThat(request.getMethod()).isEqualTo("PUT");
		assertThat(request.getHost()).isEqualTo("host");
		assertThat(request.getPort()).contains(8443);
		assertThat(request.getPath()).isEqualTo("/a/b");
		assertThat(request.getQuery()).isEqualTo("x=1");
		assertThat(request.getProtocolVersion()).isEqualTo("HTTP/2.0");
		assertThat(request.getOrigin()).isEqualTo(Origin.REMOTE);
		assertThat(request.getHeaders().get("X-Test")).containsExactly("one", "two");
		assertThat(request.getCharset()).isEqualTo(ISO_8859_1);
	}

	@Test
	void missingQueryIsEmpty() {
		assertThat(new CapturingRequest(new MockHttpServletRequest(), policy).getQuery()).isEmpty();
	}

	@Test
	void charsetFromRequest() {
		final var mock = new MockHttpServletRequest();
		mock.setCharacterEncoding("UTF-8");

		assertThat(new CapturingRequest(mock, policy).getCharset()).isEqualTo(UTF_8);
	}

	@Test
	void unknownCharsetFallsBackToIso88591() {
		final var mock = new MockHttpServletRequest();
		mock.setCharacterEncoding("no-such-charset");

		assertThat(new CapturingRequest(mock, policy).getCharset()).isEqualTo(ISO_8859_1);
	}

	@Test
	void bodyWithinLimitIsCapturedAndReplayedThroughTheReader() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/");
		mock.setContentType("text/plain");
		mock.setCharacterEncoding("UTF-8");
		mock.setContent("åäö".getBytes(UTF_8));
		final var request = new CapturingRequest(mock, new BodyCapturePolicy(10));

		request.withBody();

		assertThat(request.getBody()).isEqualTo("åäö".getBytes(UTF_8));
		assertThat(request.getReader().readLine()).isEqualTo("åäö");
		assertThat(request.getReader()).isSameAs(request.getReader());
	}

	@Test
	void withoutBodyHidesTheCapture() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/");
		mock.setContent("abc".getBytes(UTF_8));
		final var request = new CapturingRequest(mock, policy);

		request.withBody();
		request.withoutBody();

		assertThat(request.getBody()).isEmpty();
		assertThat(request.getInputStream().readAllBytes()).isEqualTo("abc".getBytes(UTF_8));
	}

	@Test
	void unreadRequestUsesTheOriginalStreams() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/");
		mock.setContent("abc".getBytes(UTF_8));
		final var request = new CapturingRequest(mock, policy);

		assertThat(request.getBody()).isEmpty();
		assertThat(request.getReader().readLine()).isEqualTo("abc");
	}

	@Test
	void bodyLargerThanItsContentLengthAllowsIsLoggedAsOmittedWithoutReading() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/");
		mock.setContentType("application/json");
		mock.setContent("abcdefgh".getBytes(UTF_8));
		final var request = new CapturingRequest(mock, policy);

		request.withBody();

		assertThat(request.getBody()).isEqualTo("{\"bodyOmitted\":\"larger than 5 bytes\"}".getBytes(UTF_8));
		assertThat(request.getInputStream().readAllBytes()).isEqualTo("abcdefgh".getBytes(UTF_8));
	}

	@Test
	void unlimitedPolicyReadsTheWholeBody() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/");
		mock.setContent(new byte[1000]);
		final var request = new CapturingRequest(mock, new BodyCapturePolicy(-1));

		request.withBody();

		assertThat(request.getBody()).hasSize(1000);
	}

	@Test
	void replayedStream() throws IOException {
		final var mock = new MockHttpServletRequest("POST", "/") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		mock.setContent("abcdefgh".getBytes(UTF_8));
		final var request = new CapturingRequest(mock, policy);
		request.withBody();
		final var input = request.getInputStream();

		assertThat(input.isReady()).isTrue();
		assertThat(input.isFinished()).isFalse();
		assertThat(input.read()).isEqualTo('a');
		assertThat(input.readAllBytes()).isEqualTo("bcdefgh".getBytes(UTF_8));
		assertThat(input.isFinished()).isTrue();
		assertThat(request.getBody()).isEqualTo("<body omitted: larger than 5 bytes>".getBytes(UTF_8));
		assertThatThrownBy(() -> input.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
		input.close();
	}
}
