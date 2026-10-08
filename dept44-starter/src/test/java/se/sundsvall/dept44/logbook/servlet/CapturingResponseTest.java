package se.sundsvall.dept44.logbook.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
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
	void singleBytesAreCaptured() throws IOException {
		final var response = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1");
		response.withBody();

		response.getOutputStream().write('a');
		response.getOutputStream().write('b');

		assertThat(response.getBody()).isEqualTo("ab".getBytes(UTF_8));
	}

	@Test
	void writerIsTheContainersAndIsCaptured() throws IOException {
		final var mock = new MockHttpServletResponse();
		mock.setCharacterEncoding("UTF-8");
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");
		response.withBody();

		response.getWriter().write("åb");
		response.getWriter().write('c');
		response.getWriter().write("xdx".toCharArray(), 1, 1);
		response.getWriter().flush();

		assertThat(response.getBody()).isEqualTo("åbcd".getBytes(UTF_8));
		assertThat(mock.getContentAsString()).isEqualTo("åbcd");
		assertThat(response.getWriter()).isSameAs(response.getWriter());

		response.getWriter().close();

		assertThat(mock.isCommitted()).isTrue();
	}

	@Test
	void writerBuffersNothingOfItsOwn() throws IOException {
		final var mock = new MockHttpServletResponse();
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");

		response.getWriter().write("abc");

		// Everything written has reached the container, so a reset of the response discards it
		assertThat(mock.getContentAsString()).isEqualTo("abc");
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
	void resetDecidesAgainWhetherToCapture() throws IOException {
		final var response = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1");
		response.withBody();
		response.setContentType("application/pdf");
		response.getOutputStream().write("%PDF".getBytes(UTF_8));

		assertThat(response.getBody()).isEmpty();

		response.reset();
		response.setContentType("application/problem+json");
		response.getOutputStream().write("{}".getBytes(UTF_8));

		assertThat(response.getBody()).isEqualTo("{}".getBytes(UTF_8));
	}

	@Test
	void withoutBodyStopsCapturing() throws IOException {
		final var response = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1");
		response.withBody();
		response.getOutputStream().write("abc".getBytes(UTF_8));

		response.withoutBody();
		response.withBody();
		response.getOutputStream().write("d".getBytes(UTF_8));

		assertThat(response.getBody()).isEmpty();
	}

	@Test
	void bodyIsCapturedOnlyForAcceptedStatuses() throws IOException {
		final var rejectedOnly = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1", status -> status == 401);
		rejectedOnly.withBody();
		rejectedOnly.getOutputStream().write("ok".getBytes(UTF_8));

		assertThat(rejectedOnly.getBody()).isEmpty();

		final var rejected = new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1", status -> status == 401);
		rejected.withBody();
		rejected.setStatus(401);
		rejected.getOutputStream().write("no".getBytes(UTF_8));

		assertThat(rejected.getBody()).isEqualTo("no".getBytes(UTF_8));
	}

	@Test
	void writerReportsAnErrorOfTheContainersWriter() throws IOException {
		final var mock = new MockHttpServletResponse() {
			@Override
			public PrintWriter getWriter() {
				// Such as after the client disconnected: the container's writer keeps the error instead of throwing it
				return new PrintWriter(Writer.nullWriter()) {
					@Override
					public boolean checkError() {
						return true;
					}
				};
			}
		};

		assertThat(new CapturingResponse(mock, policy, "HTTP/1.1").getWriter().checkError()).isTrue();
		assertThat(new CapturingResponse(new MockHttpServletResponse(), policy, "HTTP/1.1").getWriter().checkError()).isFalse();
	}

	@Test
	void failedRequestIsReportedAsServerErrorUntilCommitted() {
		final var mock = new MockHttpServletResponse();
		final var response = new CapturingResponse(mock, policy, "HTTP/1.1");

		response.failed();

		assertThat(response.getStatus()).isEqualTo(500);
		assertThat(mock.getStatus()).isEqualTo(200);

		mock.setStatus(404);
		mock.flushBuffer();

		assertThat(response.getStatus()).isEqualTo(404);
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
