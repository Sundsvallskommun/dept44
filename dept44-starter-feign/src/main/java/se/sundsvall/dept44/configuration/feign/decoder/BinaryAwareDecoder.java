package se.sundsvall.dept44.configuration.feign.decoder;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.io.InputStreamResource;

/**
 * Feign decoder that handles binary response types (byte[], InputStream, InputStreamResource) by bypassing the standard
 * message converters. Spring's ResourceHttpMessageConverter cannot read into InputStreamResource (only write from it),
 * so we need this
 * special handling.
 *
 * <p>
 * This decoder should be placed between ResponseEntityDecoder and SpringDecoder in the chain:
 * </p>
 *
 * <pre>
 * OptionalDecoder → ResponseEntityDecoder → BinaryAwareDecoder → SpringDecoder
 * </pre>
 *
 * <p>
 * Wraps another decoder for non-binary types.
 * </p>
 */
public class BinaryAwareDecoder implements Decoder {

	private final Decoder delegate;

	public BinaryAwareDecoder(final Decoder delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object decode(final Response response, final Type type) throws IOException, FeignException {
		// Handle byte[] directly
		if (type == byte[].class) {
			return readBytes(response);
		}

		// Handle InputStream directly
		if (type == InputStream.class) {
			return new ByteArrayInputStream(readBytes(response));
		}

		// Handle InputStreamResource directly
		if (type == InputStreamResource.class) {
			return new InputStreamResource(new ByteArrayInputStream(readBytes(response)));
		}

		// For all other types, delegate to the standard decoder
		return delegate.decode(response, type);
	}

	private byte[] readBytes(final Response response) throws IOException {
		if (response.body() == null) {
			return new byte[0];
		}
		try (final var inputStream = response.body().asInputStream()) {
			return inputStream.readAllBytes();
		}
	}
}
