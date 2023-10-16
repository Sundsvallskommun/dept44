package se.sundsvall.dept44.util;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class KeyStoreUtils {

    private static final ResourceLoader RESOURCE_LOADER = new PathMatchingResourcePatternResolver();

    private static final String ERROR_MESSAGE = "Unable to load key store";

    private KeyStoreUtils() { }

    /**
     * Loads a {@link KeyStore} from the given data and using the given password.
     *
     * @param keyStoreData the keystore data
     * @param keyStorePassword the keystore password
     * @return a {@link KeyStore}
     */
    public static KeyStore loadKeyStore(final byte[] keyStoreData, final String keyStorePassword) {
        try (var in = new ByteArrayInputStream(keyStoreData)) {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(in, keyStorePassword.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalStateException(ERROR_MESSAGE, e);
        }
    }

    /**
     * Loads a {@link KeyStore} from the given {@link Resource} and using the given password.
     *
     * @param resource the resource to load the keystore from
     * @param keyStorePassword the keystore password
     * @return a {@link KeyStore}
     */
    public static KeyStore loadKeyStore(final Resource resource, final String keyStorePassword) {
        try (var in = resource.getInputStream()) {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(in, keyStorePassword.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalStateException(ERROR_MESSAGE, e);
        }
    }

    /**
     * Loads a {@link KeyStore} from the given location and using the given password.
     *
     * @param keyStoreLocation the location to load the keystore from
     * @param keyStorePassword the keystore password
     * @return a {@link KeyStore}
     */
    public static KeyStore loadKeyStore(final String keyStoreLocation, final String keyStorePassword) {
        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(RESOURCE_LOADER.getResource(keyStoreLocation).getInputStream(),
                keyStorePassword.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalStateException(ERROR_MESSAGE, e);
        }
    }
}
