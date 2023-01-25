package se.sundsvall.dept44.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class ConstantsTest {

    @Test
    void test_constants() {
        assertThat(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS).isEqualTo(10);
        assertThat(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS).isEqualTo(30);
        assertThat(Constants.DEFAULT_WRITE_TIMEOUT_IN_SECONDS).isEqualTo(30);
        assertThat(Constants.APPLICATION_YAML).isEqualTo(new MediaType("application", "yaml"));
        assertThat(Constants.APPLICATION_YML).isEqualTo(new MediaType("application", "yml"));
    }
}
