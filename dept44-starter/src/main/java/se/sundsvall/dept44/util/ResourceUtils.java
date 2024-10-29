package se.sundsvall.dept44.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.FileCopyUtils.copyToString;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

import org.springframework.core.io.Resource;

public final class ResourceUtils {

	private ResourceUtils() {}

	/**
	 * Loads the given resource to a string, using UTF-8 charset.
	 *
	 * @param  resource the resource
	 * @return          the resource contents as a string
	 */
	public static String asString(final Resource resource) {
		return asString(resource, UTF_8);
	}

	/**
	 * Loads the given resource to a string, using the given charset.
	 *
	 * @param  resource the resource
	 * @param  charset  the charset
	 * @return          the resource contents as a string
	 */
	public static String asString(final Resource resource, final Charset charset) {
		try (var reader = new InputStreamReader(resource.getInputStream(), charset)) {
			return copyToString(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static String requireNotBlank(String s, String exceptionMessage) {
		if (isBlank(s)) {
			throw new IllegalArgumentException(exceptionMessage);
		}

		return s;
	}

	public static <T> T requireNonNull(T t, String exceptionMessage) {
		if (isNull(t)) {
			throw new IllegalArgumentException(exceptionMessage);
		}

		return t;
	}
}
