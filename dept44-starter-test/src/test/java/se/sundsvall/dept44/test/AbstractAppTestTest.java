package se.sundsvall.dept44.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import se.sundsvall.dept44.test.supportfiles.AppTestImplementation;
import se.sundsvall.dept44.test.supportfiles.TestBody;

import static java.time.LocalDate.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
	private Options optionsMock;

	@Mock
	private FileSource fileSourceMock;

	@Mock
	private WireMockServer wiremockMock;

	@Mock
	private WireMockConfiguration wireMockConfigMock;

	@Mock
	private ResponseDefinitionTransformerV2 extensionMock;

	@InjectMocks
	private AppTestImplementation appTest;

	@Captor
	private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

	@Test
	void testGetCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(APPLICATION_PROBLEM_JSON);

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path/123?someParam=someValue"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(restTemplateMock).exchange(eq("/some/path/123?someParam=someValue"), eq(GET), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

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

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn(".");
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(byte[].class))).thenReturn(new ResponseEntity<>(contentBytes, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isNull(); // GET requests should not have Content-Type
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testBinaryCall"));
	}

	@Test
	void testPostCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

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
		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(response, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testHandleBarReplacement"));
	}

	@Test
	void testPostCallWithMultiPart() {

		// Setup
		final var file = new File("src/test/resources/test.xml");
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(MULTIPART_FORM_DATA_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPostCallWithMultiPart"));
	}

	@Test
	void testPostCallMatchesExpectedHeaderValueWithReqexp() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("http://someurl:111222/aaa"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(null, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPostCallMatchesExpectedHeaderValueWithReqexp"));
	}

	@Test
	void testPutCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(PUT), any(), eq(String.class))).thenReturn(new ResponseEntity<>("""
			{
			"key": "this-is-key",
			"value": "this-is-value"
			}
			""", responseHeaders, NO_CONTENT));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var call = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testPutCall"));
	}

	@Test
	void testDeleteCall() {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(DELETE), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", responseHeaders, NO_CONTENT));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isNull(); // DELETE without body should not have Content-Type
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testDeleteCall"));
	}

	@Test
	void testBodyReplacement() {
		// Setup
		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<>(OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
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
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders().get(CONTENT_TYPE)).isEqualTo(List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders().get("x-test-case")).isEqualTo(List.of("AppTestImplementation.testBodyReplacement"));
		assertThat(httpEntityCaptor.getValue().getBody()).isEqualTo("{\"someKey\": \"someValue\"}");
	}

	@Test
	void testResolveBodyFileNamesWithBodyFileNameNotFound() {
		// Setup - create a stub mapping with bodyFileName that doesn't exist
		final var responseDefinition = new ResponseDefinitionBuilder()
			.withStatus(200)
			.withBodyFile("nonexistent/file.json")
			.withHeader("Content-Type", "application/json")
			.withFixedDelay(100)
			.withTransformers("response-template")
			.build();

		final var stubMapping = new StubMapping(RequestPattern.everything(), responseDefinition);

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stubMapping), null));
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", OK));

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedResponse("{}")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		// The resolveBodyFileNames method should have been called
		// Since the file doesn't exist, it should log a warning but not fail
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
	}

	@Test
	void testResolveBodyFileNamesWithBodyFileNameFound() {
		// Setup - create a stub mapping with bodyFileName that exists in __files/common/
		final var responseDefinition = new ResponseDefinitionBuilder()
			.withStatus(200)
			.withBodyFile("common/response.json")
			.withHeader("Content-Type", "application/json")
			.withFixedDelay(100)
			.withTransformers("response-template")
			.build();

		final var stubMapping = new StubMapping(RequestPattern.everything(), responseDefinition);
		stubMapping.setName("test-stub");

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		// Use an empty path so the classpath resolution uses __files/ directory
		when(fileSourceMock.getPath()).thenReturn("");
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stubMapping), null));
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", OK));

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedResponse("{}")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		// Verify that stub was removed and re-added with an inline body
		verify(wiremockMock).removeStub(any(StubMapping.class));
		verify(wiremockMock).addStubMapping(any(StubMapping.class));
	}

	@Test
	void testResolveBodyFileNamesWithBinaryBodyFileNameFound() throws Exception {
		// create a stub mapping with bodyFileName pointing to a binary file
		final var responseDefinition = new ResponseDefinitionBuilder()
			.withStatus(200)
			.withBodyFile("testBinaryCall/dept44.jpg")
			.withHeader("Content-Type", "image/jpeg")
			.build();

		final var stubMapping = new StubMapping(RequestPattern.everything(), responseDefinition);
		stubMapping.setName("binary");

		final var file = ResourceUtils.getFile("classpath:__files/testBinaryCall/dept44.jpg");
		final var expectedBytes = Files.readAllBytes(file.toPath());

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		// Use an empty path so the classpath resolution uses __files/ directory
		when(fileSourceMock.getPath()).thenReturn("");
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stubMapping), null));
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", OK));

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedResponse("{}")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();

		// Verify that stub was removed and re-added with inline binary body
		final var stubCaptor = ArgumentCaptor.forClass(StubMapping.class);
		verify(wiremockMock).removeStub(any(StubMapping.class));
		verify(wiremockMock).addStubMapping(stubCaptor.capture());

		final var capturedStub = stubCaptor.getValue();
		assertThat(capturedStub.getResponse().getByteBody()).isEqualTo(expectedBytes);
		assertThat(capturedStub.getResponse().getBodyFileName()).isNull();
	}

	@Test
	void testResolveBodyFileNamesWithFaultResponse() {
		// Setup - create a stub mapping with fault
		final var responseDefinition = new ResponseDefinitionBuilder()
			.withStatus(500)
			.withBodyFile("common/response.json")
			.withFault(Fault.CONNECTION_RESET_BY_PEER)
			.build();

		final var stubMapping = new StubMapping(RequestPattern.everything(), responseDefinition);

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stubMapping), null));
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", OK));

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedResponse("{}")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
	}

	@Test
	void testResolveBodyFileNamesWithNullResponse() {
		// Setup - create a stub mapping without response (edge case)
		final var stubMapping = new StubMapping();

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock);
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(stubMapping), null));
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<>("{}", OK));

		// Call
		final var instance = appTest.setupCall()
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withExpectedResponse("{}")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		// Stubs with null response should be filtered out and not processed
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
	}
}
