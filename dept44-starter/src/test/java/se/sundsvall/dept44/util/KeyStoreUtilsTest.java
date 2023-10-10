package se.sundsvall.dept44.util;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class KeyStoreUtilsTest {

    private static final String KEY_STORE_FILENAME = "dummy-keystore.jks";
    private static final String KEY_STORE_PASSWORD = "password";

    @Test
    void loadKeyStoreFromByteArray() throws IOException {
        try (var in = getClass().getResourceAsStream(format("/%s", KEY_STORE_FILENAME))) {
            assert in != null;

            var keyStoreData = in.readAllBytes();
            var keyStore = KeyStoreUtils.loadKeyStore(keyStoreData, KEY_STORE_PASSWORD);

            assertThat(keyStore).isNotNull();
        }
    }

    @Test
    void loadKeyStoreFromByteArrayFailure() {
        var dummyKeyStoreData = "dummy".getBytes();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> KeyStoreUtils.loadKeyStore(dummyKeyStoreData, "dummyPassword"))
            .withMessage("Unable to load key store");
    }

    @Test
    void loadKeyStoreFromResource() {
        var keyStoreResource = new ClassPathResource(KEY_STORE_FILENAME);
        var keyStore = KeyStoreUtils.loadKeyStore(keyStoreResource, KEY_STORE_PASSWORD);

        assertThat(keyStore).isNotNull();
    }

    @Test
    void loadKeyStoreFromResourceFailure() {
        var dummyKeyStoreResource = new ClassPathResource("non-existent.jks");

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> KeyStoreUtils.loadKeyStore(dummyKeyStoreResource, "dummyPassword"))
            .withMessage("Unable to load key store");
    }

    @Test
    void loadKeyStoreFromLocation() {
        var keyStore = KeyStoreUtils.loadKeyStore(format("classpath:%s", KEY_STORE_FILENAME), KEY_STORE_PASSWORD);

        assertThat(keyStore).isNotNull();
    }

    @Test
    void loadKeyStoreFromLocationFailure() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> KeyStoreUtils.loadKeyStore("non-existent.jks", "dummyPassword"))
            .withMessage("Unable to load key store");
    }
}
