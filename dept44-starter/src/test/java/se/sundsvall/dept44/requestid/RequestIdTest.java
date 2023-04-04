package se.sundsvall.dept44.requestid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdTest {

	@Test
	void testGetRequestId() {
		assertThat(RequestId.init("someId")).isTrue();
		assertThat(RequestId.reset()).isTrue();
	}

	@Test
	void testHeaderName() {
		assertThat(RequestId.HEADER_NAME).isEqualTo("x-request-id");
	}

	@Test
	void testInitAlreadyInit() {
		assertThat(RequestId.init("someId")).isTrue();
		assertThat(RequestId.init()).isFalse();
		assertThat(RequestId.reset()).isFalse();
		assertThat(RequestId.reset()).isTrue();
	}
}
