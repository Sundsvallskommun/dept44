package se.sundsvall.dept44.logbook.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BodyReplayingRequestWrapperTest {

	@Test
	void inputStream() throws IOException {
		final var wrapper = new BodyReplayingRequestWrapper(new MockHttpServletRequest(), new ByteArrayInputStream("abc".getBytes(UTF_8)));
		final var input = wrapper.getInputStream();

		assertThat(input.isReady()).isTrue();
		assertThat(input.isFinished()).isFalse();
		assertThat(input.read()).isEqualTo('a');
		assertThat(input.readAllBytes()).isEqualTo("bc".getBytes(UTF_8));
		assertThat(input.read()).isEqualTo(-1);
		assertThat(input.isFinished()).isTrue();
		assertThat(wrapper.getInputStream()).isSameAs(input);
	}

	@Test
	void readerUsesRequestEncoding() throws IOException {
		final var request = new MockHttpServletRequest();
		request.setCharacterEncoding("UTF-8");
		final var wrapper = new BodyReplayingRequestWrapper(request, new ByteArrayInputStream("åäö".getBytes(UTF_8)));

		assertThat(wrapper.getReader().readLine()).isEqualTo("åäö");
		assertThat(wrapper.getReader()).isSameAs(wrapper.getReader());
	}

	@Test
	void readerDefaultsToIso88591() throws IOException {
		final var wrapper = new BodyReplayingRequestWrapper(new MockHttpServletRequest(), new ByteArrayInputStream("abc".getBytes(UTF_8)));

		assertThat(wrapper.getReader().readLine()).isEqualTo("abc");
	}

	@Test
	void nonBlockingReadsAreNotSupported() {
		final var wrapper = new BodyReplayingRequestWrapper(new MockHttpServletRequest(), new ByteArrayInputStream(new byte[0]));

		final var input = wrapper.getInputStream();

		assertThatThrownBy(() -> input.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void closeClosesBody() throws IOException {
		final var body = mock(InputStream.class);

		new BodyReplayingRequestWrapper(new MockHttpServletRequest(), body).getInputStream().close();

		verify(body).close();
	}
}
