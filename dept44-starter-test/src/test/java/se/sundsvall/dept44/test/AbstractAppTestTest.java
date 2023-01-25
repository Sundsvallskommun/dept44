package se.sundsvall.dept44.test;

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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import net.javacrumbs.jsonunit.core.Option;
import se.sundsvall.dept44.test.supportfiles.AppTestImplementation;
import se.sundsvall.dept44.test.supportfiles.TestBody;

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
	private ResponseDefinitionTransformer extensionMock;

	@InjectMocks
	private AppTestImplementation appTest;

	@Captor
	private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

	@Test
	void testGETCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(APPLICATION_PROBLEM_JSON);

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(GET), any(), eq(String.class))).thenReturn(new ResponseEntity<String>("{}", responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
			.withServicePath("/some/path")
			.withHttpMethod(GET)
			.withHeader("headerKey", "headerValue")
			.withExpectedResponse("{}")
			.withJsonAssertOptions(List.of(Option.IGNORING_ARRAY_ORDER))
			.withExpectedResponseHeader(CONTENT_TYPE, List.of(APPLICATION_PROBLEM_JSON_VALUE))
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();

		// Verification
		assertThat(instance).isNotNull();
		verify(restTemplateMock).exchange(eq("/some/path"), eq(GET), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry(CONTENT_TYPE, List.of(APPLICATION_JSON.toString()));
		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry("x-test-case", List.of("AppTestImplementation.testGETCall"));
	}

	@Test
	void testPOSTCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<String>(null, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{}")
			.withExpectedResponseBodyIsNull()
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

		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry(CONTENT_TYPE, List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry("x-test-case", List.of("AppTestImplementation.testPOSTCall"));
	}

	@Test
	void testPOSTCallMatchesExpectedHeaderValueWithReqexp() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("http://someurl:111222/aaa"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(POST), httpEntityCaptor.capture(), eq(String.class))).thenReturn(new ResponseEntity<String>(null, responseHeaders, OK));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
			.withServicePath("/some/path")
			.withHttpMethod(POST)
			.withHeader("headerKey", "headerValue")
			.withRequest("{}")
			.withExpectedResponseBodyIsNull()
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

		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry(CONTENT_TYPE, List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry("x-test-case", List.of("AppTestImplementation.testPOSTCallMatchesExpectedHeaderValueWithReqexp"));
	}

	@Test
	void testPUTCall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(PUT), any(), eq(String.class))).thenReturn(new ResponseEntity<String>("""
			{
			  "key": "this-is-key",
			  "value": "this-is-value"
			}
			""", responseHeaders, NO_CONTENT));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
			.withServicePath("/some/path")
			.withHttpMethod(PUT)
			.withExpectedResponseStatus(NO_CONTENT)
			.sendRequestAndVerifyResponse()
			.andReturnBody(TestBody.class);

		// Verification
		assertThat(instance).isNotNull();
		assertThat(instance.getKey()).isEqualTo("this-is-key");
		assertThat(instance.getValue()).isEqualTo("this-is-value");
		verify(restTemplateMock).exchange(eq("/some/path"), eq(PUT), httpEntityCaptor.capture(), eq(String.class));
		verify(wiremockMock, times(2)).loadMappingsUsing(any(JsonFileMappingsSource.class));
		verify(wiremockMock).findAllUnmatchedRequests();
		verify(wiremockMock).verify(any());
		verify(wiremockMock).resetAll();
		verify(wireMockConfigMock, times(1)).extensions(extensionMock);

		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry(CONTENT_TYPE, List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry("x-test-case", List.of("AppTestImplementation.testPUTCall"));
	}

	@Test
	void testDELETECall() throws Exception {

		// Setup
		final var responseHeaders = new HttpHeaders();
		responseHeaders.put("responseHeader", List.of("responseValue"));

		when(wiremockMock.getOptions()).thenReturn(optionsMock).thenReturn(wireMockConfigMock); // The second invocation is a cast, hence this.
		when(optionsMock.filesRoot()).thenReturn(fileSourceMock);
		when(fileSourceMock.getPath()).thenReturn("/filepath");
		when(restTemplateMock.exchange(eq("/some/path"), eq(DELETE), any(), eq(String.class))).thenReturn(new ResponseEntity<String>("{}", responseHeaders, NO_CONTENT));
		when(wiremockMock.listAllStubMappings()).thenReturn(new ListStubMappingsResult(List.of(new StubMapping()), null));

		// Call
		final var instance = appTest.setupCall()
			.withExtensions(extensionMock)
			.withServicePath("/some/path")
			.withHttpMethod(DELETE)
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

		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry(CONTENT_TYPE, List.of(APPLICATION_JSON_VALUE));
		assertThat(httpEntityCaptor.getValue().getHeaders()).containsEntry("x-test-case", List.of("AppTestImplementation.testDELETECall"));
	}
}
