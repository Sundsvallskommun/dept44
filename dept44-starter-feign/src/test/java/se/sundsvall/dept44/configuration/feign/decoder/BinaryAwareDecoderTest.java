package se.sundsvall.dept44.configuration.feign.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.Request;
import feign.Response;
import feign.codec.Decoder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;

class BinaryAwareDecoderTest {

	private final Decoder delegateMock = mock(Decoder.class);
	private final BinaryAwareDecoder decoder = new BinaryAwareDecoder(delegateMock);

	@Test
	void testDecodeByteArray() throws Exception {
		// Setup
		final var testData = "test binary data".getBytes(StandardCharsets.UTF_8);
		final var response = createResponse(testData);

		// Call
		final var result = decoder.decode(response, byte[].class);

		// Verify
		assertThat(result).isInstanceOf(byte[].class);
		assertThat((byte[]) result).isEqualTo(testData);
		verifyNoInteractions(delegateMock);
	}

	@Test
	void testDecodeInputStream() throws Exception {
		// Setup
		final var testData = "test input stream data".getBytes(StandardCharsets.UTF_8);
		final var response = createResponse(testData);

		// Call
		final var result = decoder.decode(response, InputStream.class);

		// Verify
		assertThat(result).isInstanceOf(InputStream.class);
		assertThat(((InputStream) result).readAllBytes()).isEqualTo(testData);
		verifyNoInteractions(delegateMock);
	}

	@Test
	void testDecodeInputStreamResource() throws Exception {
		// Setup
		final var testData = "test resource data".getBytes(StandardCharsets.UTF_8);
		final var response = createResponse(testData);

		// Call
		final var result = decoder.decode(response, InputStreamResource.class);

		// Verify
		assertThat(result).isInstanceOf(InputStreamResource.class);
		assertThat(((InputStreamResource) result).getInputStream().readAllBytes()).isEqualTo(testData);
		verifyNoInteractions(delegateMock);
	}

	@Test
	void testDecodeNullBody() throws Exception {
		// Setup
		final var response = Response.builder()
			.status(200)
			.reason("OK")
			.request(Request.create(Request.HttpMethod.GET, "http://test", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
			.headers(Collections.emptyMap())
			.body((byte[]) null)
			.build();

		// Call
		final var result = decoder.decode(response, byte[].class);

		// Verify
		assertThat(result).isInstanceOf(byte[].class);
		assertThat((byte[]) result).isEmpty();
	}

	@Test
	void testDelegatesToNextDecoderForOtherTypes() throws Exception {
		// Setup
		final var testData = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);
		final var response = createResponse(testData);
		final var expectedResult = new Object();
		when(delegateMock.decode(response, String.class)).thenReturn(expectedResult);

		// Call
		final var result = decoder.decode(response, String.class);

		// Verify
		assertThat(result).isSameAs(expectedResult);
		verify(delegateMock).decode(response, String.class);
	}

	private Response createResponse(final byte[] body) {
		return Response.builder()
			.status(200)
			.reason("OK")
			.request(Request.create(Request.HttpMethod.GET, "http://test", Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
			.headers(Collections.emptyMap())
			.body(new ByteArrayInputStream(body), body.length)
			.build();
	}
}
