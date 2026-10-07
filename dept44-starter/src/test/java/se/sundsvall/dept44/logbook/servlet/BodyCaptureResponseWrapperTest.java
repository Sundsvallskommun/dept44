package se.sundsvall.dept44.logbook.servlet;

import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class BodyCaptureResponseWrapperTest {

	private final BodyCapturePolicy policy = new BodyCapturePolicy(100);

	@Test
	void binaryBodyIsExcludedOnceWhenWritingStarts() throws IOException {
		final var logbookResponse = new LogbookResponse();
		final var wrapper = new BodyCaptureResponseWrapper(new HttpServletResponseWrapper(logbookResponse), policy);
		wrapper.setContentType("application/pdf");

		wrapper.getOutputStream().write(1);
		wrapper.getOutputStream().write(2);

		assertThat(logbookResponse.withoutBodyCalls).isEqualTo(1);
		assertThat(logbookResponse.getContentAsByteArray()).containsExactly(1, 2);
	}

	@Test
	void oversizedBodyIsExcluded() throws IOException {
		final var logbookResponse = new LogbookResponse();
		final var wrapper = new BodyCaptureResponseWrapper(logbookResponse, policy);
		wrapper.setContentType("application/json");
		wrapper.setContentLength(101);

		wrapper.getWriter();

		assertThat(logbookResponse.withoutBodyCalls).isEqualTo(1);
	}

	@Test
	void textualBodyIsLeftToLogbook() throws IOException {
		final var logbookResponse = new LogbookResponse();
		final var wrapper = new BodyCaptureResponseWrapper(logbookResponse, policy);
		wrapper.setContentType("application/json");
		wrapper.setContentLength(10);

		wrapper.getWriter().write("{}");

		assertThat(logbookResponse.withoutBodyCalls).isZero();
	}

	@Test
	void withoutLogbookResponseTheBodyIsWrittenAsUsual() throws IOException {
		final var response = new MockHttpServletResponse();
		final var wrapper = new BodyCaptureResponseWrapper(response, policy);
		wrapper.setContentType("application/pdf");

		wrapper.getOutputStream().write(1);

		assertThat(response.getContentAsByteArray()).containsExactly(1);
	}

	@Test
	void findLogbookResponse() {
		final var logbookResponse = new LogbookResponse();

		assertThat(BodyCaptureResponseWrapper.findLogbookResponse(new HttpServletResponseWrapper(logbookResponse))).containsSame(logbookResponse);
		assertThat(BodyCaptureResponseWrapper.findLogbookResponse(new MockHttpServletResponse())).isEmpty();
		assertThat(BodyCaptureResponseWrapper.findLogbookResponse(null)).isEmpty();
	}

	/**
	 * Stands in for Logbook's own response wrapper, which is both a servlet response and a Logbook response.
	 */
	private static final class LogbookResponse extends MockHttpServletResponse implements HttpResponse {

		private int withoutBodyCalls;

		@Override
		public String getProtocolVersion() {
			return "HTTP/1.1";
		}

		@Override
		public Origin getOrigin() {
			return Origin.LOCAL;
		}

		@Override
		public HttpHeaders getHeaders() {
			return HttpHeaders.empty();
		}

		@Override
		public Charset getCharset() {
			return UTF_8;
		}

		@Override
		public HttpResponse withBody() {
			return this;
		}

		@Override
		public HttpResponse withoutBody() {
			withoutBodyCalls++;
			return this;
		}

		@Override
		public byte[] getBody() {
			return new byte[0];
		}
	}
}
