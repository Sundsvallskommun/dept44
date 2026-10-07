package se.sundsvall.dept44.configuration.feign.logbook;

import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalando.logbook.Origin;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

class FeignLogbookMessageTest {

	@Test
	void request() throws IOException {
		final Map<String, Collection<String>> headers = Map.of(CONTENT_TYPE, List.of("application/json"));
		final var request = FeignLogbookRequest.create(Request.create(HttpMethod.PUT, "https://host:8443/a/b?x=1", headers, "{}".getBytes(UTF_8), ISO_8859_1, null));

		assertThat(request.getRemote()).isEqualTo("localhost");
		assertThat(request.getMethod()).isEqualTo("PUT");
		assertThat(request.getScheme()).isEqualTo("https");
		assertThat(request.getHost()).isEqualTo("host");
		assertThat(request.getPort()).contains(8443);
		assertThat(request.getPath()).isEqualTo("/a/b");
		assertThat(request.getQuery()).isEqualTo("x=1");
		assertThat(request.getProtocolVersion()).isEqualTo("HTTP/1.1");
		assertThat(request.getOrigin()).isEqualTo(Origin.LOCAL);
		assertThat(request.getContentType()).isEqualTo("application/json");
		assertThat(request.getCharset()).isEqualTo(ISO_8859_1);
		assertThat(request.getHeaders().getFirst(CONTENT_LENGTH)).isEqualTo("2");
		assertThat(request.getBody()).isEmpty();
		assertThat(request.withBody().getBody()).isEqualTo("{}".getBytes(UTF_8));
		assertThat(request.withoutBody().getBody()).isEmpty();
	}

	@Test
	void requestWithoutBodyPortOrQuery() throws IOException {
		final var request = FeignLogbookRequest.create(Request.create(HttpMethod.GET, "/relative", Map.of(), null, null, null));

		assertThat(request.getScheme()).isEmpty();
		assertThat(request.getHost()).isEmpty();
		assertThat(request.getPort()).isEmpty();
		assertThat(request.getQuery()).isEmpty();
		assertThat(request.getCharset()).isEqualTo(UTF_8);
		assertThat(request.getHeaders().getFirst(CONTENT_LENGTH)).isNull();
		assertThat(request.withBody().getBody()).isEmpty();
	}

	@Test
	void requestKeepsContentLengthSetByFeign() {
		final Map<String, Collection<String>> headers = Map.of(CONTENT_LENGTH, List.of("5"));
		final var request = FeignLogbookRequest.create(Request.create(HttpMethod.POST, "http://host/", headers, "{}".getBytes(UTF_8), UTF_8, null));

		assertThat(request.getHeaders().get(CONTENT_LENGTH)).containsExactly("5");
	}

	@Test
	void protocolVersionComesFromFeign() {
		final var response = Response.builder()
			.status(200)
			.protocolVersion(Request.ProtocolVersion.HTTP_2)
			.request(Request.create(HttpMethod.GET, "http://host/", Map.of(), null, UTF_8, null))
			.headers(Map.of())
			.build();

		assertThat(FeignLogbookResponse.create(response, null).getProtocolVersion()).isEqualTo("HTTP/2.0");
		assertThat(FeignLogbookRequest.toProtocolVersion(Request.ProtocolVersion.HTTP_1_0)).isEqualTo("HTTP/1.0");
		assertThat(FeignLogbookRequest.toProtocolVersion(null)).isEqualTo("HTTP/1.1");
	}

	@Test
	void headersFromNull() {
		assertThat(FeignLogbookRequest.toLogbookHeaders(null)).isEmpty();
	}

	@Test
	void response() throws IOException {
		final var feignResponse = Response.builder()
			.status(201)
			.request(Request.create(HttpMethod.GET, "http://host/", Map.of(), null, UTF_8, null))
			.headers(Map.of(CONTENT_TYPE, List.of("text/plain")))
			.build();
		final var response = FeignLogbookResponse.create(feignResponse, "body".getBytes(UTF_8));

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getProtocolVersion()).isEqualTo("HTTP/1.1");
		assertThat(response.getOrigin()).isEqualTo(Origin.REMOTE);
		assertThat(response.getContentType()).isEqualTo("text/plain");
		assertThat(response.getCharset()).isEqualTo(UTF_8);
		assertThat(response.getBody()).isEmpty();
		assertThat(response.withBody().getBody()).isEqualTo("body".getBytes(UTF_8));
		assertThat(response.withoutBody().getBody()).isEmpty();
		assertThat(FeignLogbookResponse.create(feignResponse, null).withBody().getBody()).isEmpty();
	}
}
