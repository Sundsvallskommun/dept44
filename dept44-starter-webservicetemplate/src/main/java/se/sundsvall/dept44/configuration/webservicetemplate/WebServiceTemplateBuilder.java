package se.sundsvall.dept44.configuration.webservicetemplate;

import static se.sundsvall.dept44.util.ResourceUtils.requireNonNull;
import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

import java.security.KeyStore;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient.LogbookHttpRequestInterceptor;
import org.zalando.logbook.httpclient.LogbookHttpResponseInterceptor;

import se.sundsvall.dept44.configuration.Constants;
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
	public WebServiceTemplateBuilder withBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
		return this;
	}

	/**
	 * Password for the keystore
	 * 
	 * @param keyStorePassword password to set
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withKeyStorePassword(String keyStorePassword) {
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
	public WebServiceTemplateBuilder withKeyStoreFileLocation(String location) {
		this.keyStoreFileLocation = location;
		return this;
	}

	/**
	 * Adds an array of Strings/packages to scan.
	 * 
	 * @param packagesToScan list of strings with packages to scan.
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withPackagesToScan(List<String> packagesToScan) {
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
	public WebServiceTemplateBuilder withPackageToScan(String packageToScan) {
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
	public WebServiceTemplateBuilder withWebServiceMessageFactory(WebServiceMessageFactory webServiceMessageFactory) {
		this.webServiceMessageFactory = webServiceMessageFactory;
		return this;
	}

	/**
	 * For payload logging.
	 * 
	 * @param logbook {@link org.zalando.logbook.Logbook} to override default config with
	 * @return this builder {@link WebServiceTemplateBuilder}
	 */
	public WebServiceTemplateBuilder withLogbook(Logbook logbook) {
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
	public WebServiceTemplateBuilder withClientInterceptor(ClientInterceptor clientInterceptor) {
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
		WebServiceTemplate webServiceTemplate = new WebServiceTemplate();
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

	private void setPackagesToScan(WebServiceTemplate webServiceTemplate) {
		if (packagesToScan != null && !packagesToScan.isEmpty()) {
			Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
			marshaller.setCheckForXmlRootElement(true);
			marshaller.setPackagesToScan(packagesToScan.toArray(new String[0]));

			webServiceTemplate.setMarshaller(marshaller);
			webServiceTemplate.setUnmarshaller(marshaller);
		}
	}

	private void setMessageFactory(final WebServiceTemplate webServiceTemplate) {
		if (webServiceMessageFactory == null) {
			try {
				SaajSoapMessageFactory webMessageFactory = new SaajSoapMessageFactory(MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL));
				webMessageFactory.setMessageProperties(Collections.singletonMap(SOAPMessage.WRITE_XML_DECLARATION, Boolean.TRUE.toString()));
				webServiceTemplate.setMessageFactory(webMessageFactory);
			} catch (SOAPException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void setHttpComponentsMessageSender(final WebServiceTemplate webServiceTemplate) {
		HttpComponentsMessageSender httpComponentsMessageSender = new HttpComponentsMessageSender();

		// Only use SSL if we have a keystore and password
		if (shouldUseSSL()) {
			httpComponentsMessageSender.setHttpClient(createHttpClientWithSSL());
		} else { // Otherwise provide default
			httpComponentsMessageSender.setHttpClient(createHttpClientWithoutSSL());
		}

		if (shouldUseBasicAuth()) {
			httpComponentsMessageSender.setCredentials(
				new UsernamePasswordCredentials(basicAuthentication.username(), basicAuthentication.password()));
		}

		webServiceTemplate.setMessageSender(httpComponentsMessageSender);
	}

	private HttpClient createHttpClientWithSSL() {
		SSLContext sslcontext;
		try {
			sslcontext = SSLContexts.custom()
				.loadTrustMaterial(getKeyStore(), (chain, authType) -> true)
				.loadKeyMaterial(getKeyStore(), keyStorePassword.toCharArray())
				.build();
		} catch (Exception e) {
			throw new RuntimeException("Couldn't load keystore", e);
		}

		SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(sslcontext, NoopHostnameVerifier.INSTANCE);

		final HttpClientBuilder httpClientBuilder = getDefaultHttpClientBuilder().setSSLSocketFactory(sslConnectionFactory);

		return httpClientBuilder.build();
	}

	private HttpClient createHttpClientWithoutSSL() {
		return getDefaultHttpClientBuilder().build();
	}

	private HttpClientBuilder getDefaultHttpClientBuilder() {
		final HttpClientBuilder httpClientBuilder = HttpClients.custom()
			.addInterceptorFirst(new RemoveContentLengthHeaderInterceptor()) // Remove the content-length header since it always needs to be reset.
			.addInterceptorFirst(new RequestIdInterceptor()); // Add requestId to all requests
			
		addLogbookToHttpClientBuilder(httpClientBuilder);
		setTimeouts(httpClientBuilder);

		return httpClientBuilder;
	}

	private void setTimeouts(final HttpClientBuilder httpClientBuilder) {
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()))
			.setSocketTimeout(Math.toIntExact(readTimeout.toMillis()))
			.build();

		httpClientBuilder.setDefaultRequestConfig(requestConfig);
	}

	private KeyStore getKeyStore() throws Exception {
		KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
		store.load(new PathMatchingResourcePatternResolver().getResource(keyStoreFileLocation).getInputStream(), keyStorePassword.toCharArray());

		return store;
	}

	private void addLogbookToHttpClientBuilder(HttpClientBuilder builder) {
		if (this.logbook != null) {
			builder
				.addInterceptorFirst(new LogbookHttpRequestInterceptor(this.logbook))
				.addInterceptorFirst(new LogbookHttpResponseInterceptor());
		}
	}

	private boolean shouldUseSSL() {
		return StringUtils.isNotBlank(keyStoreFileLocation) && StringUtils.isNotBlank(keyStorePassword);
	}

	private boolean shouldUseBasicAuth() {
		return basicAuthentication != null;
	}
}
