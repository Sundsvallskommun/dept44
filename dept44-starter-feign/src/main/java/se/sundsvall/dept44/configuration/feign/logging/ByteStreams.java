package se.sundsvall.dept44.configuration.feign.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This package and all its classes exists because FeignLogbookLogger doesn't handle responses where body
 * is null when converting response.body() to byte array in method logAndRebufferResponse.
 * 
 * This class is a copy of class ByteStreams in package org.zalando.logbook.openfeign.
 * 
 * TODO: Remove this package and all its classes and change Feign configuration to use the logger provided
 * by feign logbook library when https://github.com/zalando/logbook/pull/1222 is released.
 */
final class ByteStreams {
	private ByteStreams() {
	}

	static byte[] toByteArray(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		copy(in, out);
		return out.toByteArray();
	}

	static void copy(final InputStream from, final OutputStream to) throws IOException {
		final byte[] buf = new byte[4096];
		while (true) {
			final int r = from.read(buf);
			if (r == -1) {
				break;
			}
			to.write(buf, 0, r);
		}
	}
}
