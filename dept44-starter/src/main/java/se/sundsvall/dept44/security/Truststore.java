package se.sundsvall.dept44.security;

import static java.security.KeyStore.getDefaultType;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * A class that will scan the provided path for certificates and add them in an in-memory trust store.
 * The certificates should be in X.509 (PEM) format.
 *
 * The 'trustStorePath' may be a simple path which has a one-to-one mapping to a target
 * {@link org.springframework.core.io.Resource}, or alternatively may contain the special
 * "{@code classpath*:}" prefix and/or internal Ant-style regular expressions (matched using
 * Spring's {@link org.springframework.util.AntPathMatcher} utility). Both of the latter are
 * effectively wildcards.
 *
 * <p>
 * <b>No Wildcards:</b>
 *
 * <p>
 * In the simple case, if the specified location path does not start with the {@code "classpath*:}"
 * prefix, and does not contain a PathMatcher pattern, this resolver will simply return a single
 * resource via a {@code getResource()} call on the underlying {@code ResourceLoader}. Examples are
 * real URLs such as "{@code file:C:/context.xml}", pseudo-URLs such as
 * "{@code classpath:/context.xml}", and simple unprefixed paths such as
 * "{@code /WEB-INF/context.xml}". The latter will resolve in a fashion specific to the underlying
 * {@code ResourceLoader} (e.g. {@code ServletContextResource} for a {@code WebApplicationContext}).
 *
 * <p>
 * <b>Ant-style Patterns:</b>
 *
 * <p>
 * When the path location contains an Ant-style pattern, e.g.:
 *
 * <pre class="code">
 * /WEB-INF/*-context.xml
 * com/mycompany/**&#47;applicationContext.xml
 * file:C:/some/path/*-context.xml
 * classpath:com/mycompany/**&#47;applicationContext.xml
 * </pre>
 *
 * the resolver follows a more complex but defined procedure to try to resolve the wildcard. It
 * produces a {@code Resource} for the path up to the last non-wildcard segment and obtains a
 * {@code URL} from it. If this URL is not a "{@code jar:}" URL or container-specific variant
 * (e.g. "{@code zip:}" in WebLogic, "{@code wsjar}" in WebSphere", etc.), then a
 * {@code java.io.File} is obtained from it, and used to resolve the wildcard by walking the
 * filesystem. In the case of a jar URL, the resolver either gets a {@code java.net.JarURLConnection}
 * from it, or manually parses the jar URL, and then traverses the contents of the jar file, to
 * resolve the wildcards.
 */
public class Truststore {

	private static final Logger LOG = LoggerFactory.getLogger(Truststore.class);

	private static final String MESSAGE_SSL_CONTEXT_INITIALIZATION_ERROR = "Error during initialization of SSLContext!";
	private static final String MESSAGE_ADD_CERTIFICATE_ERROR = "Error during adding of certificate '{}': '{}";
	private static final String MESSAGE_ADD_CERTIFICATE_CONFIRMATION = "Added trusted certificate: '{}'";
	private static final String MESSAGE_NO_VALID_CERTIFICATES = "Could not find any valid certificates.";
	private static final String MESSAGE_NO_RESOURCES_FOUND = "No resources found on path: '{}'";
	private static final String MESSAGE_USAGE_INFO = "Truststore enabled, with truststore path: '{}'. Use 'dept44.truststore.path' to change path to your trusted certificates";

	private static final String SSL_PROTOCOL = "TLSv1.2";
	private static final String CERTIFICATE_TYPE = "X.509";
	private static final String INTERNAL_TRUSTSTORE_PATH = "internal-truststore/*"; // Points to
																					 // src/main/resources/internal-truststore/*
																					 // in this project.

	private final String trustStorePath;
	private final String internalTrustStorePath;
	private final SSLContext sslContext;
	private TrustManagerFactory trustManagerFactory;

	/**
	 * Creates and initializes the SSLContext.
	 *
	 * If certificates are found in the path defined by 'trustStorePath' they will be added to an
	 * internal in-memory truststore.
	 *
	 * @param trustStorePath path that will be scanned for certificates (see class javadoc)
	 */
	public Truststore(final String trustStorePath) {
		this.trustStorePath = trustStorePath;
		this.internalTrustStorePath = INTERNAL_TRUSTSTORE_PATH;
		this.sslContext = initializeSSLContext();
	}

	/**
	 * Creates and initializes the SSLContext.
	 *
	 * If certificates are found in the path defined by 'trustStorePath' they will be added to an
	 * internal in-memory truststore.
	 *
	 * @param trustStorePath         path that will be scanned for certificates (see class javadoc)
	 * @param internalTrustStorePath path that will be scanned for certificates
	 */
	Truststore(final String trustStorePath, final String internalTrustStorePath) {
		this.trustStorePath = trustStorePath;
		this.internalTrustStorePath = internalTrustStorePath;
		this.sslContext = initializeSSLContext();
	}

	public TrustManagerFactory getTrustManagerFactory() {
		return this.trustManagerFactory;
	}

	public SSLContext getSSLContext() {
		return this.sslContext;
	}

	private SSLContext initializeSSLContext() {
		SSLContext initializedSSLContext = null;

		try {
			LOG.info(MESSAGE_USAGE_INFO, trustStorePath);
			initializedSSLContext = initializeTruststore();

		} catch (final Exception e) {
			LOG.error(MESSAGE_SSL_CONTEXT_INITIALIZATION_ERROR, e);
		} finally {
			if (isNull(initializedSSLContext)) {
				try {
					initializedSSLContext = SSLContext.getDefault();
				} catch (final NoSuchAlgorithmException e) {
					LOG.error(MESSAGE_SSL_CONTEXT_INITIALIZATION_ERROR, e);
				}
			}

			SSLContext.setDefault(initializedSSLContext);
		}

		return initializedSSLContext;
	}

	private SSLContext initializeTruststore() throws IOException, KeyStoreException, NoSuchAlgorithmException,
		CertificateException, KeyManagementException {

		this.trustManagerFactory = TrustManagerFactory.getInstance(getDefaultAlgorithm());

		final var certificates = fetchCertificates();

		if (isEmpty(certificates)) {
			LOG.warn(MESSAGE_NO_VALID_CERTIFICATES);
			return null;
		}

		final var keyStore = KeyStore.getInstance(getDefaultType());

		// You don't need the KeyStore instance to come from a file.
		keyStore.load(null);

		certificates.forEach(certificate -> {
			try {
				final var certificateFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
				final var x509Certificate = (X509Certificate) certificateFactory.generateCertificate(certificate
					.getInputStream());
				x509Certificate.checkValidity(); // Will prevent adding of invalid certificates.
				keyStore.setCertificateEntry(certificate.getFilename(), x509Certificate);
				LOG.info(MESSAGE_ADD_CERTIFICATE_CONFIRMATION, certificate.getFilename());
			} catch (final Exception e) {
				LOG.warn(MESSAGE_ADD_CERTIFICATE_ERROR, certificate.getFilename(), e);
			}
		});

		this.trustManagerFactory.init(keyStore);

		final var customSSLContext = SSLContext.getInstance(SSL_PROTOCOL);
		customSSLContext.init(null, this.trustManagerFactory.getTrustManagers(), new SecureRandom());
		return customSSLContext;
	}

	private List<Resource> fetchCertificates() {
		return Stream.of(
			// Get all files under the folder defined by internalTrustStorePath (dept44-internal certificates).
			fetchResources(internalTrustStorePath),
			// Get all files under the folder defined by dept44.truststore.path property.
			fetchResources(trustStorePath)).flatMap(Collection::stream).toList();
	}

	private List<Resource> fetchResources(String path) {
		try {
			return asList(new PathMatchingResourcePatternResolver().getResources(path));
		} catch (final Exception e) {
			LOG.debug(MESSAGE_NO_RESOURCES_FOUND, path);
			return emptyList();
		}
	}
}
