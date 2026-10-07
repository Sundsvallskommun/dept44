package se.sundsvall.dept44.logbook.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapturingResponseTest {

	private final BodyCapturePolicy policy = new BodyCapturePolicy(5);

	@Test
	void responseAttributes() {
		final var mock = new MockHttpServletResponse();
		mock.setStatus(201);
		mock.addHeader("X-Test", "one");
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getProtocolVersion()).isEqualTo("HTTP/1.1");
		assertThat(response.getOrigin()).isEqualTo(Origin.LOCAL);
		assertThat(response.getHeaders().get("X-Test")).containsExactly("one");
		assertThat(response.getCharset()).isEqualTo(ISO_8859_1);
	}

	@Test
	void headersWithoutNameAreLeftOut() {
		final var mock = new MockHttpServletResponse() {
			@Override
			public Collection<String> getHeaderNames() {
				final List<String> names = new ArrayList<>(super.getHeaderNames());
				names.add(null);
				return names;
			}
		};
		mock.addHeader("X-Test", "one");

		assertThat(new CapturingResponse(mock, policy, "HTTP/1.1").getHeaders()).containsOnlyKeys("X-Test");
	}

	@Test
	void unknownCharsetFallsBackToIso88591() {
		final var mock = new MockHttpServletResponse() {
			@Override
			public String getCharacterEncoding() {
				return "no-such-charset";
			}
		};

		assertThat(new CapturingResponse(mock, policy, "HTTP/1.1").getCharset()).isEqualTo(ISO_8859_1);
	}

	@Test
	void bodyIsNotCapturedWithoutBeingAskedFor() throws IOException {
		final var response = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1");

		response.getOutputStream().write("abc".getBytes(UTF_8));

		assertThat(response.getBody()).isEmpty();
	}

	@Test
	void writerAndSingleBytesAreCaptured() throws IOException {
		final var mock = new MockHttpServletResponse();
		mock.setCharacterEncoding("UTF-8");
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");
		response.withBody();

		response.getOutputStream().write('a');
		response.getWriter().write("bc");
		response.flushBuffer();

		assertThat(response.getBody()).isEqualTo("abc".getBytes(UTF_8));
		assertThat(mock.getContentAsString()).isEqualTo("abc");
		assertThat(response.getWriter()).isSameAs(response.getWriter());
	}

	@Test
	void resetDropsWhatWasCaptured() throws IOException {
		final var mock = new MockHttpServletResponse();
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");
		response.withBody();
		response.getOutputStream().write("abc".getBytes(UTF_8));

		response.resetBuffer();
		response.getOutputStream().write("d".getBytes(UTF_8));

		assertThat(response.getBody()).isEqualTo("d".getBytes(UTF_8));

		response.reset();

		assertThat(response.getBody()).isEmpty();
	}

	@Test
	void withoutBodyStopsCapturing() throws IOException {
		final var response = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1");
		response.withBody();
		response.getOutputStream().write("abc".getBytes(UTF_8));

		response.withoutBody();

		assertThat(response.getBody()).isEmpty();
	}

	@Test
	void outputStreamDelegates() throws IOException {
		final var mock = new MockHttpServletResponse();
		final var output = new CapturingResponse(mock, policy, "HTTP/1.1").getOutputStream();

		assertThat(output.isReady()).isTrue();
		assertThatThrownBy(() -> output.setWriteListener(null)).isInstanceOf(UnsupportedOperationException.class);
		output.flush();
		output.close();

		assertThat(mock.isCommitted()).isTrue();
	}
}
