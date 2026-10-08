package se.sundsvall.dept44.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.javacrumbs.jsonunit.JsonAssert;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import se.sundsvall.dept44.test.supportfiles.AppTestImplementation;
import se.sundsvall.dept44.test.supportfiles.TestBody;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.time.LocalDate.now;
import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.MediaType.IMAGE_JPEG;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@ExtendWith(MockitoExtension.class)
class AbstractAppTestTest {

	@Mock
	private TestRestTemplate restTemplateMock;

	@Mock
	private WireMockServer wiremockMock;

	@Mock
	private ResponseDefinitionTransformerV2 extensionMock;

	@InjectMocks
	private AppTestImplementation appTest;

	@Captor
	private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

	@AfterEach
	void resetJsonAssertOptions() {
		JsonAssert.resetOptions();
	}

	@Test
	void testSetupCallRestoresDefaultJsonAssertOptions() {
		appTest.setupCall().withJsonAssertOptions(List.of(IGNORING_EXTRA_FIELDS));

		appTest.setupCall();

		assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> assertJsonEquals("{}", "{\"extra\": 1}"));
		assertJsonEquals("[1, 2]", "[2, 1]");
	}

	@Test
	void testWithJsonAssertOptionsResetsToStrictForNullAndEmptyList() {
		appTest.setupCall().withJsonAssertOptions(null);
		assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> assertJsonEquals("[1, 2]", "[2, 1]"));

		appTest.setupCall().withJsonAssertOptions(List.of());
		assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> assertJsonEquals("[1, 2]", "[2, 1]"));
	}

	@Test
	void testServicePathFunctionStartsFromAnEmptyBuilderOnEveryCall() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/a?x=1")).willReturn(ok()));
			realAppTest.setupCall()
				.withServicePath(uriBuilder -> uriBuilder.path("/a").queryParam("x", "1").build())
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			server.stubFor(get(urlEqualTo("/b")).willReturn(ok()));
			realAppTest.setupCall()
				.withServicePath(uriBuilder -> uriBuilder.path("/b").build())
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThat(server.findAll(getRequestedFor(urlEqualTo("/a?x=1")))).hasSize(1);
			assertThat(server.findAll(getRequestedFor(urlEqualTo("/b")))).hasSize(1);
		});
	}

	@Test
	void testServicePathFunctionIsSentWithoutBeingEncodedAgain() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/p/%C3%A5%20%C3%A4?q=a%20b%26c")).willReturn(ok()));

			realAppTest.setupCall()
				.withServicePath(uriBuilder -> uriBuilder.path("/p/{segment}").queryParam("q", "{q}").build("å ä", "a b&c"))
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThat(server.findAll(getRequestedFor(urlEqualTo("/p/%C3%A5%20%C3%A4?q=a%20b%26c")))).hasSize(1);
		});
	}

	@Test
	void testServicePathStringReplacesAnEarlierServicePathFunction() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/string")).willReturn(ok()));

			realAppTest.setupCall()
				.withServicePath(uriBuilder -> uriBuilder.path("/function").build())
				.withServicePath("/string")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThat(server.findAll(getRequestedFor(urlEqualTo("/string")))).hasSize(1);
		});
	}

	@Test
	void testExpectedResponseHeaderWithSeveralValues() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/headers")).willReturn(ok().withHeader("X-Multi", "first", "second")));

			realAppTest.setupCall()
				.withServicePath("/headers")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponseHeader("X-Multi", List.of("first", "sec.*"))
				.sendRequest();

			realAppTest.setupCall()
				.withServicePath("/headers")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponseHeader("X-Multi", List.of("first", "third"));
			assertThatExceptionOfType(AssertionError.class).isThrownBy(realAppTest::sendRequest);

			realAppTest.setupCall()
				.withServicePath("/headers")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponseHeader("X-Multi", List.of("first"));
			assertThatExceptionOfType(AssertionError.class).isThrownBy(realAppTest::sendRequest);
		});
	}

	@Test
	void testUnexpectedStatusIsReportedBeforeHeadersWithTheResponseBody() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/missing")).willReturn(notFound().withBody("{\"detail\": \"No such thing\"}")));

			realAppTest.setupCall()
				.withServicePath("/missing")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponseHeader("X-Absent", List.of("value"));

			assertThatExceptionOfType(AssertionError.class)
				.isThrownBy(realAppTest::sendRequest)
				.withMessageContaining("No such thing")
				.withMessageContaining("404");
		});
	}

	@Test
	void testVerifyStubsRetriesWithinASecond() {
		final var stub = new StubMapping();
		final var served = servedBy(stub);
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stub), null));
		when(wiremockMock.getAllServeEvents()).thenReturn(List.of()).thenReturn(List.of(served));

		final var start = System.nanoTime();
		appTest.verifyStubs();

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
		verify(wiremockMock, times(2)).getAllServeEvents();
		verify(wiremockMock).resetAll();
	}

	@Test
	void testWithExtensionsIsNotSupported() {
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> appTest.withExtensions(extensionMock))
			.withMessageContaining("when the WireMock server is created");
	}

	@Test
	void testVerifyAllStubsFailsForAStubThatWasNotCalledWhenAnotherStubForTheSameUrlWas() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/items?page=1")).withName("page one").willReturn(ok()));
			server.stubFor(get(urlEqualTo("/items?page=2")).withName("page two").willReturn(ok()));
			server.stubFor(post(urlEqualTo("/items?page=1")).willReturn(ok()));

			realAppTest.setupCall()
				.withServicePath("/items?page=1")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThatExceptionOfType(VerificationException.class)
				.isThrownBy(realAppTest::verifyAllStubs)
				.withMessageContaining("page two")
				.withMessageContaining("POST /items?page=1")
				.withMessageNotContaining("page one");
		});
	}

	@Test
	void testVerifyAllStubsTreatsAStubLoadedAgainAsTheSameStub() {
		runAgainstServer((server, realAppTest) -> {
			// Loading the same stub file twice (as repeated setupCall() does) gives two stubs with different ids
			server.stubFor(get(urlEqualTo("/items?page=1")).willReturn(ok()));
			server.stubFor(get(urlEqualTo("/items?page=1")).willReturn(ok()));

			realAppTest.setupCall()
				.withServicePath("/items?page=1")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThat(realAppTest.verifyAllStubs()).isTrue();
		});
	}

	@Test
	void testVerifyAllStubsPassesWhenEveryStubWasCalled() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/items?page=1")).willReturn(ok()));

			realAppTest.setupCall()
				.withServicePath("/items?page=1")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.sendRequest();

			assertThat(realAppTest.verifyAllStubs()).isTrue();
		});
	}

	@Test
	void testNonJsonResponsesAreComparedWithoutIgnoringAllWhitespace() {
		runAgainstServer((server, realAppTest) -> {
			server.stubFor(get(urlEqualTo("/text")).willReturn(ok("Anna  Svensson\n").withHeader(CONTENT_TYPE, "text/plain")));
			server.stubFor(get(urlEqualTo("/xml")).willReturn(ok("<person><name>Anna Svensson</name></person>").withHeader(CONTENT_TYPE, "application/xml")));

			realAppTest.setupCall()
				.withServicePath("/text")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponse("Anna Svensson")
				.sendRequest();
			realAppTest.setupCall()
				.withServicePath("/xml")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponse("<person>\n\t<name>Anna Svensson</name>\n</person>")
				.sendRequest();

			assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> realAppTest.setupCall()
				.withServicePath("/text")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponse("AnnaSvensson")
				.sendRequest());
			assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> realAppTest.setupCall()
				.withServicePath("/xml")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(OK)
				.withExpectedResponse("<person><name>AnnaSvensson</name></person>")
				.sendRequest());
		});
	}

	@Test
	void testAndVerifyThatRetriesWithinASecond() {
		final var calls = new AtomicInteger();

		final var start = System.nanoTime();
		appTest.andVerifyThat(() -> calls.incrementAndGet() > 1);

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
		assertThat(calls).hasValue(2);
	}

	@Test
	void testGetCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(APPLICATION_PROBLEM_JSON);

		when(restTemplateMock.exchange(eq(URI.create("/some/path/123?someParam=someValue")), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath(uriBuilder -> uriBuilder.path("/some/path/{value}")
				.queryParam("someParam", "someValue")
				.build(123))
			// .withServicePath("/some/path")
			.withHttpMethod(GET)
			.withHeader("headerKey", "headerValue")
			.withExpectedResponse("{}")
			.withJsonAssertOptions(List.of(Option.IGNORING_ARRAY_ORDER))
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseHeader(CONTENT_TYPE, List.of(APPLICATION_PROBLEM_JSON_VALUE))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq(URI.create("/some/path/123?someParam=someValue")), eq(GET), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isNull(); // GET requests should not have Content-Type
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testGetCall"));

		// Verification of reset-method
		assertThat(appTest.reset()).hasAllNullFieldsOrPropertiesExcept(
			"logger",
			"contentType",
			"expectedResponseBodyIsNull",
			"maxVerificationDelayInSeconds",
			"expectedResponseType",
			"restTemplate",
			"wiremock");
	}

	@Test
	void testBinaryCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(IMAGE_JPEG);
		final var file = ResourceUtils.getFile("classpath:__files/testBinaryCall/dept44.jpg");
		final var contentBytes = Files.readAllBytes(file.toPath());

		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(byte[].class))).thenReturn(new ResponseEntity<>(contentBytes, responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedBinaryResponse("dept44.jpg")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(GET), httpEntityCaptor.capture(), eq(byte[].class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isNull(); // GET requests should not have Content-Type
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testBinaryCall"));
	}

	@Test
	void testPostCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{}")
			.withExpectedResponseBodyIsNull()
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseHeader("responseHeader", List.of("responseValue"))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(POST), any(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPostCall"));
	}

	@Test
	void testHandleBarReplacement() {
		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		final var response = """
			{
				"id": "id-%s,
				"responseData": "testData"
			}
			""".formatted(now().getYear());
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(response, responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{\"requestData\":\"testData\"}")
			.withExpectedResponse("""
				{
					"id": "id-{{now format='yyyy'}},
					"responseData": "{{request.body.requestData}}"
				}
				""")
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseHeader("responseHeader", List.of("responseValue"))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(POST), any(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testHandleBarReplacement"));
	}

	@Test
	void testPostCallWithMultiPart() {

		// Setup
		final var file = new File("src/test/resources/test.xml");
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHeader("headerKey", "headerValue")
			.withHttpMethod(POST)
			.withContentType(MULTIPART_FORM_DATA)
			.withRequestFile("file", file)
			.withExpectedResponseBodyIsNull()
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseHeader("responseHeader", List.of("responseValue"))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(POST), any(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(MULTIPART_FORM_DATA_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPostCallWithMultiPart"));
	}

	@Test
	void testPostCallMatchesExpectedHeaderValueWithReqexp() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("http://someurl:111222/aaa"));

		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{}")
			.withExpectedResponseBodyIsNull()
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseHeader("responseHeader", List.of("^http://(.*)/(.*)$"))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(POST), any(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPostCallMatchesExpectedHeaderValueWithReqexp"));
	}

	@Test
	void testPutCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(restTemplateMock.exchange(eq("/some/path"), eq(PUT), any(), eq(String.class))).thenReturn(new ResponseEntity<>("""
			{
			"key": "this-is-key",
			"value": "this-is-value"
			}
			""", responseHeaders, NO_CONTENT));
		stubWasCalled();

		// Call
		final var call = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(PUT)
			.withExpectedResponseStatus(NO_CONTENT)
			.withMaxVerificationDelayInSeconds(5)
			.sendRequestAndVerifyResponse();

		final var instance = call.getResponseBody(TestBody.class);
		final var headers = call.getResponseHeaders();

		// Verification
		assertThat(instance).isNotNull();
		assertThat(instance.getKey()).isEqualTo("this-is-key");
		assertThat(instance.getValue()).isEqualTo("this-is-value");

		assertThat(headers).isNotNull();
		assertThat(headers.get("responseHeader")).containsOnly("responseValue");

		verify(restTemplateMock).exchange(eq("/some/path"), eq(PUT), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPutCall"));
	}

	@Test
	void testDeleteCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(restTemplateMock.exchange(eq("/some/path"), eq(DELETE), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", responseHeaders, NO_CONTENT));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(DELETE)
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseStatus(NO_CONTENT)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(DELETE), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isNull(); // DELETE without body should not have Content-Type
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testDeleteCall"));
	}

	@Test
	void testBodyReplacement() {
		// Setup
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(OK));
		stubWasCalled();

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{\"someKey\": \"[replaceme]\"}")
			.withRequestReplacement("[replaceme]", "someValue")
			.withExpectedResponseBodyIsNull()
			.withMaxVerificationDelayInSeconds(5)
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(POST), any(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).getAllServeEvents();
		verify(wiremockMock).resetAll();

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testBodyReplacement"));
		assertThat(httpEntityCaptor.getValue().getBody()).isEqualTo("{\"someKey\": \"someValue\"}");
	}

	private void stubWasCalled() {
		final var stub = new StubMapping();
		final var served = servedBy(stub);
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stub), null));
		when(wiremockMock.getAllServeEvents()).thenReturn(List.of(served));
	}

	private static ServeEvent servedBy(final StubMapping stub) {
		final var serveEvent = mock(ServeEvent.class);
		when(serveEvent.getWasMatched()).thenReturn(true);
		when(serveEvent.getStubMapping()).thenReturn(stub);
		return serveEvent;
	}

	private static void runAgainstServer(final BiConsumer<WireMockServer, AppTestImplementation> test) {
		final var server = new WireMockServer(options().dynamicPort());
		server.start();
		try {
			final var realAppTest = new AppTestImplementation();
			realAppTest.wiremock = server;
			realAppTest.restTemplate = new TestRestTemplate(new RestTemplateBuilder().rootUri(server.baseUrl()));
			test.accept(server, realAppTest);
		} finally {
			server.stop();
		}
	}
}
