package se.sundsvall.dept44.configuration.webservicetemplate;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls a real HTTPS server, to show which servers the template trusts.
 * <p>
 * The keystores in {@code tls/} (password {@code password}) were made with keytool: {@code server.p12} holds a
 * certificate for {@code localhost} issued by "Test CA", {@code client.p12} a client key whose certificate chain is
 * [client, Test CA], {@code unknown-server.p12} a self-signed certificate for {@code localhost}, and
 * {@code trusts-unknown-server.p12} that self-signed certificate as a trusted entry.
 */
class WebServiceTemplateBuilderTlsTest {

	private static final String PASSWORD = "password";
	private static final String CLIENT_KEY_STORE = "classpath:tls/client.p12";
	private static final String RESPONSE = """
		<?xml version="1.0" encoding="UTF-8"?>
		<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body><pong xmlns="urn:test"/></soapenv:Body></soapenv:Envelope>""";

	private HttpsServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void serverIssuedByTheCaOfTheClientCertificateIsTrusted() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://localhost:" + startServer("server.p12") + "/")
			.withKeyStoreFileLocation(CLIENT_KEY_STORE)
			.withKeyStorePassword(PASSWORD)
			.build();

		assertThat(call(template)).isTrue();
	}

	@Test
	void serverTrustedByNothingIsRejected() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://localhost:" + startServer("unknown-server.p12") + "/")
			.withKeyStoreFileLocation(CLIENT_KEY_STORE)
			.withKeyStorePassword(PASSWORD)
			.build();

		assertThatThrownBy(() -> call(template))
			.isInstanceOf(WebServiceIOException.class)
			.hasCauseInstanceOf(SSLHandshakeException.class);
	}

	@Test
	void serverCertificateForAnotherHostIsRejected() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://127.0.0.1:" + startServer("server.p12") + "/")
			.withKeyStoreFileLocation(CLIENT_KEY_STORE)
			.withKeyStorePassword(PASSWORD)
			.build();

		assertThatThrownBy(() -> call(template))
			.isInstanceOf(WebServiceIOException.class)
			.hasCauseInstanceOf(SSLHandshakeException.class)
			.hasMessageContaining("127.0.0.1");
	}

	@Test
	void serverTrustedByTheGivenTrustManagerFactoryIsTrusted() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://localhost:" + startServer("unknown-server.p12") + "/")
			.withKeyStoreFileLocation(CLIENT_KEY_STORE)
			.withKeyStorePassword(PASSWORD)
			.withTrustManagerFactory(trustManagerFactory("trusts-unknown-server.p12"))
			.build();

		assertThat(call(template)).isTrue();
	}

	@Test
	void givenTrustManagerFactoryIsUsedWithoutAKeyStore() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://localhost:" + startServer("unknown-server.p12") + "/")
			.withTrustManagerFactory(trustManagerFactory("trusts-unknown-server.p12"))
			.build();

		assertThat(call(template)).isTrue();
	}

	@Test
	void withoutKeyStoreAndTrustManagerFactoryTheJvmDefaultTrustIsUsed() throws Exception {
		final var template = new WebServiceTemplateBuilder()
			.withBaseUrl("https://localhost:" + startServer("unknown-server.p12") + "/")
			.build();

		assertThatThrownBy(() -> call(template))
			.isInstanceOf(WebServiceIOException.class)
			.hasCauseInstanceOf(SSLHandshakeException.class);
	}

	private static boolean call(final WebServiceTemplate template) {
		return template.sendSourceAndReceiveToResult(new StringSource("<ping xmlns=\"urn:test\"/>"), new StringResult());
	}

	private int startServer(final String keyStore) throws IOException, GeneralSecurityException {
		final var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(loadKeyStore(keyStore), PASSWORD.toCharArray());
		final var sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

		// Every local address, so that both localhost and 127.0.0.1 reach it
		server = HttpsServer.create(new InetSocketAddress(0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			final var body = RESPONSE.getBytes(UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			try (var responseBody = exchange.getResponseBody()) {
				responseBody.write(body);
			}
		});
		server.start();
		return server.getAddress().getPort();
	}

	private static TrustManagerFactory trustManagerFactory(final String keyStore) throws IOException, GeneralSecurityException {
		final var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(loadKeyStore(keyStore));
		return trustManagerFactory;
	}

	private static KeyStore loadKeyStore(final String name) throws IOException, GeneralSecurityException {
		final var keyStore = KeyStore.getInstance("PKCS12");
		try (var input = new ClassPathResource("tls/" + name).getInputStream()) {
			keyStore.load(input, PASSWORD.toCharArray());
		}
		return keyStore;
	}
}
