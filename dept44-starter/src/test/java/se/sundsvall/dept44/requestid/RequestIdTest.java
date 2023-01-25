package se.sundsvall.dept44.requestid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    @Test
    void testGetRequestId() {
        assertThat(RequestId.init("someId")).isTrue();
        assertThat(RequestId.reset()).isTrue();
    }

    @Test
    void testHeaderName() {
        assertThat(RequestId.HEADER_NAME).isEqualTo("X-Request-ID");
    }

    @Test
    void testInitAlreadyInit() {
        assertThat(RequestId.init("someId")).isTrue();
        assertThat(RequestId.init()).isFalse();
        assertThat(RequestId.reset()).isFalse();
        assertThat(RequestId.reset()).isTrue();
    }
}
