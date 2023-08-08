package se.sundsvall.dept44.configuration.webservicetemplate;

import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponents5MessageSender;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient5.LogbookHttpRequestInterceptor;
import org.zalando.logbook.httpclient5.LogbookHttpResponseInterceptor;

import se.sundsvall.dept44.configuration.Constants;
import se.sundsvall.dept44.configuration.webservicetemplate.exception.WebserviceTemplateException;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.DefaultFaultInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.RemoveContentLengthHeaderInterceptor;
import se.sundsvall.dept44.configuration.webservicetemplate.interceptor.RequestIdInterceptor;
import se.sundsvall.dept44.support.BasicAuthentication;

public class WebServiceTemplateBuilder {

	private String baseUrl;
	private String keyStoreFileLocation;
	private String keyStorePassword;

	private Duration connectTimeout = Duration.ofSeconds(Constants.DEFAULT_CONNECT_TIMEOUT_IN_SECONDS);
	private Duration readTimeout = Duration.ofSeconds(Constants.DEFAULT_READ_TIMEOUT_IN_SECONDS);

	private BasicAuthentication basicAuthentication;
	private Set<ClientInterceptor> clientInterceptors;
	private Logbook logbook;
	private Set<String> packagesToScan;
	private WebServiceMessageFactory webServiceMessageFactory;

	public static WebServiceTemplateBuilder create() {
		return new WebServiceTemplateBuilder();
	}

	/**
	 * Url..
	 * 
	 * @param baseUrl url to use
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withBaseUrl(final String baseUrl) {
		this.baseUrl = baseUrl;
		return this;
	}

	/**
	 * Password for the keystore
	 * 
	 * @param keyStorePassword password to set
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStorePassword(final String keyStorePassword) {
		this.keyStorePassword = requireNotBlank(keyStorePassword, "keystore password must be set");
		return this;
	}

	/**
	 * The file location of the keystore
	 * 
	 * @param location can be a classpath (e.g. classpath:keystore.p12) or a file location (e.g.
	 *                 src/main/resources/keystore.p12).
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStoreFileLocation(final String location) {
		this.keyStoreFileLocation = location;
		return this;
	}

	/**
	 * Adds an array of Strings/packages to scan.
	 * 
	 * @param packagesToScan list of strings with packages to scan.
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withPackagesToScan(final List<String> packagesToScan) {
		if (this.packagesToScan == null) {
			this.packagesToScan = new HashSet<>(packagesToScan.size());
		}
		this.packagesToScan.addAll(packagesToScan);
		return this;
	}

	/**
	 * Adds a package to be scanned. Package will be added to a list of packages which will all be scanned during build.
	 * 
	 * @param packageToScan which package to scan
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withPackageToScan(final String packageToScan) {
		if (this.packagesToScan == null) {
			this.packagesToScan = new HashSet<>();
		}

		this.packagesToScan.add(packageToScan);
		return this;
	}

	/**
	 * Optional, if not set a default will be created.
	 * 
	 * @param webServiceMessageFactory messageFactory to override with
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withWebServiceMessageFactory(final WebServiceMessageFactory webServiceMessageFactory) {
		this.webServiceMessageFactory = webServiceMessageFactory;
		return this;
	}

	/**
	 * For payload logging.
	 * 
	 * @param logbook {@link org.zalando.logbook.Logbook} to override default config with
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withLogbook(final Logbook logbook) {
		this.logbook = logbook;
		return this;
	}

	/**
	 * Sets the connect timeout.
	 *
	 * @param connectTimeout the connect timeout
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withConnectTimeout(final Duration connectTimeout) {
		this.connectTimeout = requireNonNull(connectTimeout, "connectTimeout may not be null");
		return this;
	}

	/**
	 * Sets the read timeout.
	 *
	 * @param readTimeout the read timeout
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withReadTimeout(final Duration readTimeout) {
		this.readTimeout = requireNonNull(readTimeout, "readTimeout may not be null");
		return this;
	}

	/**
	 * Sets basic authentication.
	 *
	 * @param username the Basic authentication username
	 * @param password the Basic authentication password
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withBasicAuthentication(final String username, final String password) {
		basicAuthentication = new BasicAuthentication(username, password);
		return this;
	}

	/**
	 * Adds an interceptor.
	 * 
	 * @param clientInterceptor interceptor to add
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withClientInterceptor(final ClientInterceptor clientInterceptor) {
		if (this.clientInterceptors == null) {
			this.clientInterceptors = new HashSet<>();
		}
		this.clientInterceptors.add(clientInterceptor);
		return this;
	}

	/**
	 * Build the WebServiceTemplate
	 * 
	 * @return a configured WebServiceTemplate
	 */
	public WebServiceTemplate build() {
		var webServiceTemplate = new WebServiceTemplate();
		webServiceTemplate.setDefaultUri(baseUrl);

		setClientInterceptors(webServiceTemplate);
		setPackagesToScan(webServiceTemplate);
		setMessageFactory(webServiceTemplate);
		setHttpComponentsMessageSender(webServiceTemplate);

		return webServiceTemplate;
	}

	private void setClientInterceptors(final WebServiceTemplate webServiceTemplate) {
		if (this.clientInterceptors == null) {
			// Create a default interceptors.
			withClientInterceptor(new DefaultFaultInterceptor());
		}
		
		webServiceTemplate.setInterceptors(clientInterceptors.toArray(new ClientInterceptor[0]));
	}

	private void setPackagesToScan(final WebServiceTemplate webServiceTemplate) {
		if (packagesToScan != null && !packagesToScan.isEmpty()) {
			var marshaller = new Jaxb2Marshaller();
			marshaller.setCheckForXmlRootElement(true);
			marshaller.setPackagesToScan(packagesToScan.toArray(new String[0]));

			webServiceTemplate.setMarshaller(marshaller);
			webServiceTemplate.setUnmarshaller(marshaller);
		}
	}

	private void setMessageFactory(final WebServiceTemplate webServiceTemplate) {
		if (webServiceMessageFactory == null) {
			try {
				var webMessageFactory = new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL));
				webMessageFactory.setMessageProperties(Collections.singletonMap(SOAPMessage.WRITE_XML_DECLARATION, Boolean.TRUE.toString()));
				webServiceTemplate.setMessageFactory(webMessageFactory);
			} catch (SOAPException e) {
				throw new WebserviceTemplateException("Error when setting message factory", e);
			}
		}
	}

	private void setHttpComponentsMessageSender(final WebServiceTemplate webServiceTemplate) {
		var httpComponents5MessageSender = new HttpComponents5MessageSender();

		if (shouldUseBasicAuth()) {
			httpComponents5MessageSender.setCredentials(
				new UsernamePasswordCredentials(basicAuthentication.username(), basicAuthentication.password().toCharArray()));
		}

		httpComponents5MessageSender.setHttpClient(createHttpClient());

		webServiceTemplate.setMessageSender(httpComponents5MessageSender);
	}

	private HttpClient createHttpClient() {
		return getDefaultHttpClientBuilder()
			.setConnectionManager(createConnectionManager())
			.build();
	}

	private HttpClientBuilder getDefaultHttpClientBuilder() {
		var httpClientBuilder = HttpClients.custom()
			// Remove the content-length header since it always needs to be reset
			.addRequestInterceptorFirst(new RemoveContentLengthHeaderInterceptor())
			// Add request-id to all requests
			.addRequestInterceptorFirst(new RequestIdInterceptor());

		if (logbook != null) {
			httpClientBuilder
				.addRequestInterceptorFirst(new LogbookHttpRequestInterceptor(logbook))
				.addResponseInterceptorFirst(new LogbookHttpResponseInterceptor());
		}

		return httpClientBuilder;
	}

	private HttpClientConnectionManager createConnectionManager() {
		var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
			.setDefaultConnectionConfig(ConnectionConfig.custom()
				.setSocketTimeout(Timeout.ofMilliseconds(Math.toIntExact(readTimeout.toMillis())))
				.setConnectTimeout(Timeout.ofMilliseconds(Math.toIntExact(connectTimeout.toMillis())))
				.build());

		// Set up SSL if keystore and password are set
		if (shouldUseSSL()) {
			SSLContext sslContext;
			try {
				sslContext = SSLContexts.custom()
					.loadTrustMaterial(getKeyStore(), (chain, authType) -> true)
					.loadKeyMaterial(getKeyStore(), keyStorePassword.toCharArray())
					.build();
			} catch (Exception e) {
				throw new WebserviceTemplateException("Couldn't load keystore", e);
			}

			var sslConnectionSocketFactory = SSLConnectionSocketFactoryBuilder.create()
				.setSslContext(sslContext)
				.setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
				.build();

			return connectionManagerBuilder.setSSLSocketFactory(sslConnectionSocketFactory).build();
		}

		return connectionManagerBuilder.build();
	}

	private KeyStore getKeyStore() throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException {
		var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(new PathMatchingResourcePatternResolver().getResource(keyStoreFileLocation).getInputStream(), keyStorePassword.toCharArray());
		return keyStore;
	}

	private boolean shouldUseSSL() {
		return StringUtils.isNotBlank(keyStoreFileLocation) && StringUtils.isNotBlank(keyStorePassword);
	}

	private boolean shouldUseBasicAuth() {
		return basicAuthentication != null;
	}
}
