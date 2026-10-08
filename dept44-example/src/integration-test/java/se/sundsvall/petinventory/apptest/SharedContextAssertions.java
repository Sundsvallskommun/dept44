package se.sundsvall.petinventory.apptest;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions shared by the test classes that share their application context.
 */
final class SharedContextAssertions {

	private static final Set<ApplicationContext> CONTEXTS = ConcurrentHashMap.newKeySet();

	private SharedContextAssertions() {}

	/**
	 * Asserts that the stub in the mappings directory of the running class, loaded by WireMock when it was reset before
	 * the test, answers with the body file of that class.
	 */
	static void assertClassStubAnswers(final WireMockServer wiremock, final String expectedBody) throws IOException, InterruptedException {
		try (final var client = HttpClient.newHttpClient()) {
			final var response = client.send(HttpRequest.newBuilder(URI.create(wiremock.baseUrl() + "/shared-context/class")).build(), BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).isEqualToIgnoringWhitespace(expectedBody);
		}
	}

	/**
	 * Asserts that every class sharing its context in this JVM has been given the same application context.
	 */
	static void assertSameContext(final ApplicationContext applicationContext) {
		CONTEXTS.add(applicationContext);

		assertThat(CONTEXTS).hasSize(1);
	}
}
