package se.sundsvall.dept44.configuration.webservicetemplate;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponents5ClientFactory;
import org.springframework.ws.transport.http.SimpleHttpComponents5MessageSender;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient5.LogbookHttpRequestInterceptor;
import se.sundsvall.dept44.configuration.Constants;
import se.sundsvall.dept44.configuration.webservicetemplate.exception.WebServiceTemplateException;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.BoundedLogbookHttpResponseInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.DefaultFaultInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.RemoveContentLengthHeaderInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.RequestIdInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.ssl.AnyOfTrustManager;
import se.sundsvall.dept44.logbook.BodyCapturePolicy;
import se.sundsvall.dept44.support.BasicAuthentication;

import static java.util.HashSet.newHashSet;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static se.sundsvall.dept44.util.KeyStoreUtils.loadKeyStore;
import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

public class WebServiceTemplateBuilder {

	private static final String TLS = "TLS";

	private String baseUrl;
	private String keyStoreFileLocation;
	private byte[] keyStoreData;
	private String keyStorePassword;

	private Duration connectTimeout = Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS);
	private Duration readTimeout = Duration.ofSeconds(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS);

	private BasicAuthentication basicAuthentication;
	private TrustManagerFactory trustManagerFactory;
	private Set<ClientInterceptor> clientInterceptors;
	private Logbook logbook;
	private BodyCapturePolicy bodyCapturePolicy = BodyCapturePolicy.withDefaultLimit();
	private Set<String> packagesToScan;
	private WebServiceMessageFactory webServiceMessageFactory;

	public static WebServiceTemplateBuilder create() {
		return new WebServiceTemplateBuilder();
	}

	/**
	 * Url..
	 *
	 * @param  baseUrl url to use
	 * @return         this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withBaseUrl(final String baseUrl) {
		this.baseUrl = baseUrl;
		return this;
	}

	/**
	 * Password for the keystore.
	 *
	 * @param  keyStorePassword password to set
	 * @return                  this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStorePassword(final String keyStorePassword) {
		this.keyStorePassword = requireNotBlank(keyStorePassword, "keystore password must be set");
		return this;
	}

	/**
	 * The file location of the keystore.
	 *
	 * @param  location can be a classpath (e.g., classpath: keystore.p12) or a file location (e.g.,
	 *                  src/main/resources/keystore.p12).
	 * @return          this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStoreFileLocation(final String location) {
		this.keyStoreFileLocation = location;
		return this;
	}

	/**
	 * The keystore as a byte array.
	 *
	 * @param  keyStoreData the keyStore as a byte array
	 * @return              this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStoreData(final byte[] keyStoreData) {
		this.keyStoreData = keyStoreData;
		return this;
	}

	/**
	 * Adds an array of Strings/packages to scan.
	 *
	 * @param  packagesToScan list of strings with packages to scan.
	 * @return                this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withPackagesToScan(final List<String> packagesToScan) {
		if (this.packagesToScan == null) {
			this.packagesToScan = newHashSet(packagesToScan.size());
		}
		this.packagesToScan.addAll(packagesToScan);
		return this;
	}

	/**
	 * Adds a package to be scanned. Package will be added to a list of packages which will all be scanned during build.
	 *
	 * @param  packageToScan which package to scan
	 * @return               this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withPackageToScan(final String packageToScan) {
		if (this.packagesToScan == null) {
			this.packagesToScan = new HashSet<>();
		}

		this.packagesToScan.add(packageToScan);
		return this;
	}

	/**
	 * Optional, if not set, a default will be created.
	 *
	 * @param  webServiceMessageFactory messageFactory to override with
	 * @return                          this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withWebServiceMessageFactory(final WebServiceMessageFactory webServiceMessageFactory) {
		this.webServiceMessageFactory = webServiceMessageFactory;
		return this;
	}

	/**
	 * For payload logging. At most {@link BodyCapturePolicy#DEFAULT_MAX_BODY_SIZE} of a response body is held in memory
	 * for it; use {@link #withLogbook(Logbook, BodyCapturePolicy)} to apply the service's own
	 * {@code logbook.logs.maxBodySizeToCapture}.
	 *
	 * @param  logbook {@link org.zalando.logbook.Logbook} to override default config with
	 * @return         this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withLogbook(final Logbook logbook) {
		return withLogbook(logbook, BodyCapturePolicy.withDefaultLimit());
	}

	/**
	 * For payload logging, holding at most what the given policy allows of a response body in memory.
	 *
	 * @param  logbook           {@link org.zalando.logbook.Logbook} to override default config with
	 * @param  bodyCapturePolicy the {@link BodyCapturePolicy} bean
	 * @return                   this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withLogbook(final Logbook logbook, final BodyCapturePolicy bodyCapturePolicy) {
		this.logbook = logbook;
		this.bodyCapturePolicy = requireNonNull(bodyCapturePolicy, "bodyCapturePolicy may not be null");
		return this;
	}

	/**
	 * Sets the connect timeout.
	 *
	 * @param  connectTimeout the connect timeout
	 * @return                this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withConnectTimeout(final Duration connectTimeout) {
		this.connectTimeout = requireNonNull(connectTimeout, "connectTimeout may not be null");
		return this;
	}

	/**
	 * Sets the read timeout.
	 *
	 * @param  readTimeout the read timeout
	 * @return             this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withReadTimeout(final Duration readTimeout) {
		this.readTimeout = requireNonNull(readTimeout, "readTimeout may not be null");
		return this;
	}

	/**
	 * Sets basic authentication.
	 *
	 * @param  username the Basic authentication username
	 * @param  password the Basic authentication password
	 * @return          this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withBasicAuthentication(final String username, final String password) {
		basicAuthentication = new BasicAuthentication(username, password);
		return this;
	}

	/**
	 * Which servers to trust. A server is trusted when its certificate chain is trusted by this factory, or by the
	 * certificates in the keystore when one is set. When not set, the JVM default trust store is used instead of this
	 * factory; pass {@code truststore.getTrustManagerFactory()} to trust dept44's truststore.
	 *
	 * @param  trustManagerFactory an initialized trust manager factory
	 * @return                     this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withTrustManagerFactory(final TrustManagerFactory trustManagerFactory) {
		this.trustManagerFactory = trustManagerFactory;
		return this;
	}

	/**
	 * Adds an interceptor. Interceptors run in the order they are added.
	 *
	 * @param  clientInterceptor interceptor to add
	 * @return                   this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withClientInterceptor(final ClientInterceptor clientInterceptor) {
		if (this.clientInterceptors == null) {
			this.clientInterceptors = new LinkedHashSet<>();
		}
		this.clientInterceptors.add(clientInterceptor);
		return this;
	}

	/**
	 * Build the WebServiceTemplate.
	 *
	 * @return a configured WebServiceTemplate
	 */
	public WebServiceTemplate build() {
		if (isNotBlank(keyStoreFileLocation) && isNotEmpty(keyStoreData)) {
			throw new WebServiceTemplateException("Only one of 'keyStoreFileLocation' and 'keyStoreData' may be set");
		}

		final var webServiceTemplate = new WebServiceTemplate();
		webServiceTemplate.setDefaultUri(baseUrl);

		setClientInterceptors(webServiceTemplate);
		setPackagesToScan(webServiceTemplate);
		setMessageFactory(webServiceTemplate);
		setHttpComponentsMessageSender(webServiceTemplate);

		return webServiceTemplate;
	}

	private void setClientInterceptors(final WebServiceTemplate webServiceTemplate) {
		if (this.clientInterceptors == null) {
			// Create a default interceptor.
			withClientInterceptor(new DefaultFaultInterceptor());
		}

		webServiceTemplate.setInterceptors(clientInterceptors.toArray(new ClientInterceptor[0]));
	}

	private void setPackagesToScan(final WebServiceTemplate webServiceTemplate) {
		if ((packagesToScan != null) && !packagesToScan.isEmpty()) {
			final var marshaller = new Jaxb2Marshaller();
			marshaller.setCheckForXmlRootElement(true);
			marshaller.setPackagesToScan(packagesToScan.toArray(new String[0]));

			webServiceTemplate.setMarshaller(marshaller);
			webServiceTemplate.setUnmarshaller(marshaller);
		}
	}

	private void setMessageFactory(final WebServiceTemplate webServiceTemplate) {
		if (webServiceMessageFactory == null) {
			try {
				final var webMessageFactory = new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL));
				webMessageFactory.setMessageProperties(Collections.singletonMap(SOAPMessage.WRITE_XML_DECLARATION, Boolean.TRUE.toString()));
				webServiceTemplate.setMessageFactory(webMessageFactory);
			} catch (final SOAPException e) {
				throw new WebServiceTemplateException("Error when setting message factory", e);
			}
		}
	}

	private void setHttpComponentsMessageSender(final WebServiceTemplate webServiceTemplate) {
		final var factory = new HttpComponents5ClientFactory();
		factory.setConnectionTimeout(connectTimeout);
		factory.setReadTimeout(readTimeout);

		if (shouldUseBasicAuth()) {
			factory.setCredentials(new UsernamePasswordCredentials(
				basicAuthentication.username(),
				basicAuthentication.password().toCharArray()));
		}

		factory.addClientBuilderCustomizer(this::customizeHttpClientBuilder);
		factory.addConnectionManagerBuilderCustomizer(this::customizeConnectionManagerBuilder);

		webServiceTemplate.setMessageSender(new SimpleHttpComponents5MessageSender(factory));
	}

	private void customizeHttpClientBuilder(final HttpClientBuilder httpClientBuilder) {
		httpClientBuilder
			.addRequestInterceptorFirst(new RemoveContentLengthHeaderInterceptor())
			.addRequestInterceptorFirst(new RequestIdInterceptor());

		if (logbook != null) {
			httpClientBuilder
				.addRequestInterceptorFirst(new LogbookHttpRequestInterceptor(logbook))
				.addResponseInterceptorFirst(new BoundedLogbookHttpResponseInterceptor(bodyCapturePolicy));
		}
	}

	private void customizeConnectionManagerBuilder(final PoolingHttpClientConnectionManagerBuilder cmBuilder) {
		cmBuilder.setDefaultConnectionConfig(ConnectionConfig.custom()
			.setSocketTimeout(Timeout.ofMilliseconds(Math.toIntExact(readTimeout.toMillis())))
			.setConnectTimeout(Timeout.ofMilliseconds(Math.toIntExact(connectTimeout.toMillis())))
			.build());

		if (shouldUseSSL() || trustManagerFactory != null) {
			try {
				final var keyStore = shouldUseSSL() ? getKeyStore() : null;
				final var sslContext = SSLContext.getInstance(TLS);
				sslContext.init(keyManagers(keyStore), new TrustManager[] {
					serverTrustManager(keyStore)
				}, null);

				// With neither a host name verifier nor a policy given, the TLS layer itself checks that the server
				// certificate is issued for the host (HTTPS endpoint identification), as for any JSSE connection.
				cmBuilder.setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext));
			} catch (final Exception e) {
				throw new WebServiceTemplateException("Couldn't set up TLS", e);
			}
		}
	}

	private KeyManager[] keyManagers(final KeyStore keyStore) throws GeneralSecurityException {
		if (keyStore == null) {
			return null;
		}
		final var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
		return keyManagerFactory.getKeyManagers();
	}

	/**
	 * The server is trusted when its certificate chain is trusted by the trust manager factory given to this builder
	 * (the JVM default trust store when none is given), or by any certificate in the client keystore: its trusted
	 * certificates, and every certificate in the chain of its keys, so also the CA that issued the client certificate.
	 */
	private X509TrustManager serverTrustManager(final KeyStore keyStore) throws GeneralSecurityException {
		final var trustManagerFactories = new ArrayList<TrustManagerFactory>();
		trustManagerFactories.add(trustManagerFactory != null ? trustManagerFactory : trustManagerFactory(null));
		keyStoreCertificates(keyStore).ifPresent(trustManagerFactories::add);

		return new AnyOfTrustManager(trustManagerFactories.stream()
			.map(TrustManagerFactory::getTrustManagers)
			.flatMap(Arrays::stream)
			.filter(X509TrustManager.class::isInstance)
			.map(X509TrustManager.class::cast)
			.toList());
	}

	/**
	 * A trust manager factory for every certificate in the keystore. Initialized with the keystore itself, it would only
	 * trust the client's own certificate of each key entry, not the certificates that issued it.
	 */
	private static Optional<TrustManagerFactory> keyStoreCertificates(final KeyStore keyStore) throws GeneralSecurityException {
		if (keyStore == null) {
			return Optional.empty();
		}
		final var certificates = KeyStore.getInstance(KeyStore.getDefaultType());
		try {
			certificates.load(null, null);
		} catch (final IOException e) {
			throw new GeneralSecurityException("Couldn't create an empty keystore", e);
		}
		for (final var alias : Collections.list(keyStore.aliases())) {
			final var chain = certificates(keyStore, alias);
			for (var index = 0; index < chain.size(); index++) {
				certificates.setCertificateEntry(alias + "-" + index, chain.get(index));
			}
		}
		// A trust manager without any certificate fails every check with an unrelated error, so leave it out
		return certificates.size() == 0 ? Optional.empty() : Optional.of(trustManagerFactory(certificates));
	}

	private static TrustManagerFactory trustManagerFactory(final KeyStore trustStore) throws GeneralSecurityException {
		final var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(trustStore);
		return factory;
	}

	/**
	 * The certificate chain of a key entry, or the certificate of a trusted certificate entry.
	 */
	private static List<Certificate> certificates(final KeyStore keyStore, final String alias) throws KeyStoreException {
		final var chain = keyStore.getCertificateChain(alias);
		if (chain != null) {
			return List.of(chain);
		}
		return Optional.ofNullable(keyStore.getCertificate(alias)).map(List::of).orElseGet(List::of);
	}

	private KeyStore getKeyStore() {
		if (isNotBlank(keyStoreFileLocation)) {
			return loadKeyStore(keyStoreFileLocation, keyStorePassword);
		}
		return loadKeyStore(keyStoreData, keyStorePassword);
	}

	private boolean shouldUseSSL() {
		return (isNotBlank(keyStoreFileLocation) || isNotEmpty(keyStoreData)) && isNotBlank(keyStorePassword);
	}

	private boolean shouldUseBasicAuth() {
		return basicAuthentication != null;
	}
}
