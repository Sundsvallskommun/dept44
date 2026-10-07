package se.sundsvall.dept44.logbook.servlet;

import java.io.ByteArrayOutputStream;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

/**
 * Copy of a body for payload logging that never grows past the {@link BodyCapturePolicy} limit. Once the body turns
 * out to be larger, the copy is dropped and nothing more is kept.
 */
final class BoundedCapture {

	private static final byte[] EMPTY = new byte[0];

	private final BodyCapturePolicy policy;
	private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	private boolean overflowed;

	BoundedCapture(final BodyCapturePolicy policy) {
		this.policy = policy;
	}

	void write(final byte[] bytes, final int offset, final int length) {
		if (overflowed) {
			return;
		}
		if (policy.exceedsLimit((long) buffer.size() + length)) {
			overflowed = true;
			buffer = null;
			return;
		}
		buffer.write(bytes, offset, length);
	}

	void write(final int b) {
		write(new byte[] {
			(byte) b
		}, 0, 1);
	}

	boolean isOverflowed() {
		return overflowed;
	}

	byte[] toByteArray() {
		if (overflowed) {
			return EMPTY;
		}
		return buffer.toByteArray();
	}

	void reset() {
		overflowed = false;
		buffer = new ByteArrayOutputStream();
	}
}
