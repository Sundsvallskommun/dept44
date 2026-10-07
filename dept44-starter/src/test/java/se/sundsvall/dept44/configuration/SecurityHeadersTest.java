package se.sundsvall.dept44.configuration;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.EXPIRES;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * Security and cache headers as a client receives them from a running server, where Spring Security's header writer
 * runs before dept44's {@code DisableBrowserCacheFilter}.
 */
@SpringBootTest(classes = SecurityHeadersTest.TestApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class SecurityHeadersTest {

	private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
	private static final String X_FRAME_OPTIONS = "X-Frame-Options";
	private static final String X_XSS_PROTECTION = "X-XSS-Protection";

	private static final Map<String, List<String>> SECURITY_HEADERS = Map.of(
		X_CONTENT_TYPE_OPTIONS, List.of("nosniff"),
		X_FRAME_OPTIONS, List.of("DENY"),
		X_XSS_PROTECTION, List.of("0"));

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void securityHeadersAreWrittenBeforeTheRequestIsHandled() {
		webTestClient.get().uri("/headers/seen-by-handler")
			.exchange()
			.expectStatus().isOk()
			.expectBody(String.class).isEqualTo("nosniff|DENY|0");
	}

	@Test
	void eachHeaderIsWrittenOnce() {
		final var headers = get("/headers/plain");

		assertSecurityHeaders(headers);
		assertThat(headers.get(CACHE_CONTROL)).containsExactly("no-store");
		assertThat(headers.get(PRAGMA)).containsExactly("no-cache");
		assertThat(headers.get(EXPIRES)).containsExactly("0");
	}

	@Test
	void eachHeaderIsWrittenOnceOnStreamedResponse() {
		final var headers = get("/headers/streamed");

		assertSecurityHeaders(headers);
		assertThat(headers.get(CACHE_CONTROL)).containsExactly("no-store");
		assertThat(headers.get(PRAGMA)).containsExactly("no-cache");
		assertThat(headers.get(EXPIRES)).containsExactly("0");
	}

	@Test
	void cacheControlSetByServiceIsKept() {
		final var headers = get("/headers/own-cache-control");

		assertSecurityHeaders(headers);
		assertThat(headers.get(CACHE_CONTROL)).containsExactly("public, max-age=60");
	}

	private HttpHeaders get(final String uri) {
		return webTestClient.get().uri(uri)
			.exchange()
			.expectStatus().isOk()
			.expectBody().returnResult()
			.getResponseHeaders();
	}

	private static void assertSecurityHeaders(final HttpHeaders headers) {
		SECURITY_HEADERS.forEach((name, values) -> assertThat(headers.get(name)).as(name).containsExactlyElementsOf(values));
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		HeadersController headersController() {
			return new HeadersController();
		}
	}

	@RestController
	static class HeadersController {

		@GetMapping(path = "/headers/seen-by-handler", produces = TEXT_PLAIN_VALUE)
		String seenByHandler(final HttpServletResponse response) {
			return String.join("|", response.getHeader(X_CONTENT_TYPE_OPTIONS), response.getHeader(X_FRAME_OPTIONS), response.getHeader(X_XSS_PROTECTION));
		}

		@GetMapping(path = "/headers/plain", produces = TEXT_PLAIN_VALUE)
		String plain() {
			return "plain";
		}

		@GetMapping(path = "/headers/streamed")
		ResponseEntity<StreamingResponseBody> streamed() {
			return ResponseEntity.ok()
				.contentType(TEXT_PLAIN)
				.body(output -> output.write("streamed".getBytes(UTF_8)));
		}

		@GetMapping(path = "/headers/own-cache-control", produces = TEXT_PLAIN_VALUE)
		String ownCacheControl(final HttpServletResponse response) {
			response.setHeader(CACHE_CONTROL, "public, max-age=60");
			return "cached";
		}
	}
}
