package se.sundsvall.dept44.logbook.servlet;

import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class BoundedCaptureTest {

	@Test
	void keepsBytesUpToTheLimit() {
		final var capture = new BoundedCapture(new BodyCapturePolicy(5));

		capture.write("abc".getBytes(UTF_8), 0, 3);
		capture.write('d');
		capture.write('e');

		assertThat(capture.isOverflowed()).isFalse();
		assertThat(capture.toByteArray()).isEqualTo("abcde".getBytes(UTF_8));
	}

	@Test
	void dropsEverythingOnceTheLimitIsPassed() {
		final var capture = new BoundedCapture(new BodyCapturePolicy(5));

		capture.write("abcde".getBytes(UTF_8), 0, 5);
		capture.write('f');
		capture.write("ghi".getBytes(UTF_8), 0, 3);

		assertThat(capture.isOverflowed()).isTrue();
		assertThat(capture.toByteArray()).isEmpty();
	}

	@Test
	void resetStartsOver() {
		final var capture = new BoundedCapture(new BodyCapturePolicy(2));
		capture.write("abc".getBytes(UTF_8), 0, 3);

		capture.reset();
		capture.write('x');

		assertThat(capture.isOverflowed()).isFalse();
		assertThat(capture.toByteArray()).isEqualTo("x".getBytes(UTF_8));
	}

	@Test
	void unlimitedPolicyKeepsEverything() {
		final var capture = new BoundedCapture(new BodyCapturePolicy(-1));

		capture.write(new byte[1000], 0, 1000);

		assertThat(capture.toByteArray()).hasSize(1000);
	}
}
