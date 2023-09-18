package se.sundsvall.dept44.test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.fromOneOf;
import static java.lang.Class.forName;
import static java.lang.String.format;
import static java.nio.file.Files.readString;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.setOptions;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.ResourceUtils.getFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.javacrumbs.jsonunit.JsonAssert;
import net.javacrumbs.jsonunit.core.Option;

public abstract class AbstractAppTest {

	private static final String FILES_DIR = "__files/";
	private static final String COMMON_MAPPING_DIR = "/common";
	private static final String MAPPING_DIRECTORY = "/mappings";
	private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static final int DEFAULT_VERIFICATION_DELAY_IN_SECONDS = 5;
	private static final Class<?> DEFAULT_RESPONSE_TYPE = String.class;
	private static final MediaType DEFAULT_CONTENT_TYPE = APPLICATION_JSON;

	private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

	private boolean expectedResponseBodyIsNull;
	private int maxVerificationDelayInSeconds = DEFAULT_VERIFICATION_DELAY_IN_SECONDS;
	private MultiValueMap<String, Object> multipartBody;
	private String requestBody;
	private String expectedResponseBody;
	private byte[] expectedResponseBinary;
	private Class<?> expectedResponseType = DEFAULT_RESPONSE_TYPE;
	private String mappingPath;
	private String servicePath;
	private String responseBody;
	private HttpHeaders responseHeaders;
	private HttpMethod method;
	private MediaType contentType = DEFAULT_CONTENT_TYPE;
	private HttpStatus expectedResponseStatus;
	private HttpHeaders expectedResponseHeaders;
	private Map<String, String> headerValues;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected WireMockServer wiremock;

	public AbstractAppTest reset() {
		this.expectedResponseBodyIsNull = false;
		this.multipartBody = null;
		this.requestBody = null;
		this.expectedResponseBody = null;
		this.expectedResponseBinary = null;
		this.expectedResponseType = DEFAULT_RESPONSE_TYPE;
		this.mappingPath = null;
		this.servicePath = null;
		this.responseBody = null;
		this.responseHeaders = null;
		this.method = null;
		this.contentType = DEFAULT_CONTENT_TYPE;
		this.expectedResponseStatus = null;
		this.expectedResponseHeaders = null;
		this.headerValues = null;

		return this;
	}

	public AbstractAppTest setupCall() {

		reset();
		initializeJsonAssert();

		// Fetch test case name.
		final var testCaseName = getTestMethodName();

		this.mappingPath = wiremock.getOptions().filesRoot().getPath();
		if (!this.mappingPath.endsWith("/")) {
			this.mappingPath += "/";
		}

		this.wiremock.loadMappingsUsing(new JsonFileMappingsSource(
			new ClasspathFileSource(this.mappingPath + FILES_DIR + COMMON_MAPPING_DIR + MAPPING_DIRECTORY)));
		if (nonNull(testCaseName)) {
			this.wiremock.loadMappingsUsing(new JsonFileMappingsSource(
				new ClasspathFileSource(this.mappingPath + FILES_DIR + testCaseName + MAPPING_DIRECTORY)));
		}

		return this;
	}

	public AbstractAppTest withExtensions(final Extension... extensions) {
		((WireMockConfiguration) wiremock.getOptions()).extensions(extensions);
		return this;
	}

	public AbstractAppTest withHttpMethod(final HttpMethod method) {
		this.method = method;
		return this;
	}

	public AbstractAppTest withContentType(final MediaType contentType) {
		this.contentType = contentType;
		return this;
	}

	public AbstractAppTest withExpectedResponseStatus(final HttpStatus status) {
		this.expectedResponseStatus = status;
		return this;
	}

	public AbstractAppTest withExpectedResponseType(final Class<?> type) {
		this.expectedResponseType = type;
		return this;
	}

	public AbstractAppTest withHeader(final String key, final String value) {
		if (isNull(this.headerValues)) {
			this.headerValues = new HashMap<>();
		}
		this.headerValues.put(key, value);
		return this;
	}

	/**
	 * Set expected response header.
	 *
	 * @param expectedHeaderKey   the expected header key.
	 * @param expectedHeaderValue the list of expected header values, as regular expressions.
	 */
	public AbstractAppTest withExpectedResponseHeader(final String expectedHeaderKey, final List<String> expectedHeaderValue) {
		if (isNull(this.expectedResponseHeaders)) {
			this.expectedResponseHeaders = new HttpHeaders();
		}
		this.expectedResponseHeaders.put(expectedHeaderKey, expectedHeaderValue);
		return this;
	}

	/**
	 * Method takes a JSON response string or a file name where the response can be
	 * read from
	 *
	 * @param expectedResponse raw json response string or filename where the response can be
	 *                         read from
	 */
	public AbstractAppTest withExpectedResponse(final String expectedResponse) {
		final var contentFromFile = fromTestFile(expectedResponse);
		if (nonNull(contentFromFile)) {
			this.expectedResponseBody = contentFromFile;
		} else {
			this.expectedResponseBody = expectedResponse;
		}

		return this;
	}

	/**
	 * Method takes a file name to a binary file.
	 *
	 * @param  expectedResponseFile the filename where the binary response can be read from.
	 * @throws IOException
	 */
	public AbstractAppTest withExpectedBinaryResponse(final String expectedResponseFile) throws IOException {
		final var file = ResourceUtils.getFile("classpath:" + this.mappingPath + FILES_DIR + getTestMethodName() + "/" + expectedResponseFile);
		this.expectedResponseBinary = Files.readAllBytes(file.toPath());
		this.expectedResponseType = byte[].class;

		return this;
	}

	public AbstractAppTest withExpectedResponseBodyIsNull() {
		this.expectedResponseBodyIsNull = true;
		return this;
	}

	public AbstractAppTest withServicePath(final String servicePath) {
		this.servicePath = servicePath;
		return this;
	}

	/**
	 * Method adds options to be used when assertion of json is done, for example IGNORING_EXTRA_ARRAY_ITEMS.
	 * By default the test will compare arrays with option to ignore array order. If the need to use
	 * maximum strictness in JsonAssert - send in null or an empty list to just reset options to
	 * JsonAsserts default ones.
	 *
	 * @param options list of options to use when doing the json assertion or null/empty list for resetting
	 *                to JsonAssert defaults (strict comparison)
	 */
	public AbstractAppTest withJsonAssertOptions(final List<Option> options) {
		// Reset to JsonAssert strict assertion options (removing option IGNORING_ARRAY_ORDER)
		JsonAssert.resetOptions();

		if (nonNull(options)) {
			// Set sent in assertion options
			if (options.size() == 1) {
				setOptions(options.get(0));
			} else {
				setOptions(options.get(0), options.subList(1, options.size()).toArray(new Option[0]));
			}
		}
		return this;
	}

	/**
	 * Method takes a JSON request string or a file name where the request can be
	 * read from.
	 *
	 * @param  request raw JSON request string or filename where the request can be
	 *                 read from.
	 * @return
	 */
	public AbstractAppTest withRequest(final String request) {
		final var contentFromFile = fromTestFile(request);
		if (nonNull(contentFromFile)) {
			this.requestBody = contentFromFile;
		} else {
			this.requestBody = request;
		}
		return this;
	}

	/**
	 * Method takes a file that will be added to the multipart body.
	 *
	 * @param  file to be added to the request as a multipart.
	 * @return
	 */
	public AbstractAppTest withRequestFile(final String parameterName, final File file) {
		if (isNull(this.multipartBody)) {
			this.multipartBody = new LinkedMultiValueMap<>();
		}

		multipartBody.add(parameterName, new FileSystemResource(file));

		return this;
	}

	/**
	 * Set max verification delay in seconds.
	 *
	 * I.e. the maximum time to spend while verifying a condition.
	 *
	 * @param  maxVerificationDelayInSeconds the number of seconds that the verification logic will try before failing.
	 * @return
	 */
	public AbstractAppTest withMaxVerificationDelayInSeconds(final int maxVerificationDelayInSeconds) {
		this.maxVerificationDelayInSeconds = maxVerificationDelayInSeconds;
		return this;
	}

	public AbstractAppTest sendRequestAndVerifyResponse() {
		logger.info(getTestMethodName());

		final var requestEntity = nonNull(this.multipartBody) ? restTemplateRequest(this.contentType, this.multipartBody) : restTemplateRequest(this.contentType, this.requestBody);

		// Call service and fetch response.
		final var response = this.restTemplate.exchange(this.servicePath, this.method, requestEntity, expectedResponseType);
		this.responseBody = nonNull(response.getBody()) ? String.valueOf(response.getBody()) : null;
		this.responseHeaders = response.getHeaders();

		if (nonNull(this.expectedResponseHeaders)) {
			this.expectedResponseHeaders.entrySet().stream().forEach(expectedHeader -> {
				assertThat(response.getHeaders()).containsKey(expectedHeader.getKey());
				assertThat(response.getHeaders().getValuesAsList(expectedHeader.getKey()))
					.allMatch(actualHeaderValue -> expectedHeader.getValue().stream()
						.allMatch(expectedHeaderValue -> equalsIgnoreCase(expectedHeaderValue, actualHeaderValue) ||
							Pattern.matches(expectedHeaderValue, actualHeaderValue)));
			});
		}
		assertThat(response.getStatusCode()).isEqualTo(this.expectedResponseStatus);
		if (nonNull(this.expectedResponseBody)) {
			final var responseContentType = response.getHeaders().getContentType();
			if (nonNull(responseContentType) && responseContentType.isPresentIn(List.of(APPLICATION_JSON, APPLICATION_PROBLEM_JSON))) {
				// Compare as JSON
				assertJsonEquals(this.expectedResponseBody, this.responseBody);
			} else {
				// Compare as text
				assertThat(this.expectedResponseBody).isEqualToIgnoringWhitespace(this.responseBody);
			}
		}
		if (nonNull(this.expectedResponseBinary)) {
			// Compare as Binary
			assertThat(this.expectedResponseBinary).isEqualTo(response.getBody());
		}
		if (this.expectedResponseBodyIsNull) {
			assertThat(this.responseBody).isNull();
		}

		await()
			.atMost(maxVerificationDelayInSeconds, SECONDS)
			.pollDelay(0, SECONDS)
			.pollInterval(1, SECONDS)
			.ignoreExceptions()
			.until(this::verifyAllStubs);

		this.wiremock.resetAll();

		return this;
	}

	/**
	 * Returns the response body mapped to the provided class.
	 *
	 * @param  clazz the class to map the response body to
	 * @return       the mapped response
	 */
	public <T> T getResponseBody(final Class<T> clazz) throws JsonProcessingException, ClassNotFoundException {
		return andReturnBody(clazz);
	}

	/**
	 * Returns the response body mapped to the provided type reference.
	 *
	 * @param  typeReference the type reference to map the response body to
	 * @return               the mapped response
	 */
	public <T> T getResponseBody(final TypeReference<T> typeReference) throws JsonProcessingException {
		return andReturnBody(typeReference);
	}

	/**
	 * Method returns the received server response mapped to the sent in class
	 *
	 * @param  clazz class to map response to
	 * @return       response mapped to sent in class type
	 */
	public <T> T andReturnBody(final Class<T> clazz) throws JsonProcessingException, ClassNotFoundException {
		return clazz.cast(JSON_MAPPER.readValue(this.responseBody, forName(clazz.getName())));
	}

	/**
	 * Returns the received server response mapped to the given type reference
	 *
	 * @param  typeReference the type reference to map response to
	 * @return               response mapped to given type reference
	 */
	public <T> T andReturnBody(final TypeReference<T> typeReference) throws JsonProcessingException {
		return JSON_MAPPER.readValue(this.responseBody, typeReference);
	}

	/**
	 * Returns the response headers.
	 *
	 * @return the response headers
	 */
	public HttpHeaders getResponseHeaders() {
		return responseHeaders;
	}

	public AbstractAppTest andVerifyThat(final Callable<Boolean> conditionIsMet) {
		await()
			.atMost(maxVerificationDelayInSeconds, SECONDS)
			.pollDelay(0, SECONDS)
			.pollInterval(1, SECONDS)
			.until(conditionIsMet);

		return this;
	}

	private HttpEntity<Object> restTemplateRequest(final MediaType mediaType, Object body) {
		final var httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(mediaType);
		httpHeaders.add("x-test-case", getClass().getSimpleName() + "." + getTestMethodName());
		if (!isEmpty(this.headerValues)) {
			this.headerValues.forEach(httpHeaders::add);
		}
		return new HttpEntity<>(body, httpHeaders);
	}

	protected String fromTestFile(final String fileName) {
		return fromFile(this.mappingPath + FILES_DIR + getTestMethodName() + "/" + fileName);
	}

	private String fromFile(final String filePath) {
		try {
			return readString(getFile("classpath:" + filePath).toPath());
		} catch (final IOException e) {
			return null;
		}
	}

	private String getTestMethodName() {
		return Arrays.stream(Thread.currentThread().getStackTrace())
			.map(StackTraceElement::getMethodName)
			.filter(methodName -> methodName.startsWith("test"))
			.findFirst()
			.orElseThrow(() -> new UnsupportedOperationException("Could not find method name! Test method must start with 'test'"));
	}

	private void initializeJsonAssert() {
		JsonAssert.setTolerance(0); // Activates mathematical equivalence (i.e. 1.0 == 1.000)
		JsonAssert.setOptions(Option.IGNORING_ARRAY_ORDER);
	}

	/**
	 * Verifies that all setup stubs setup have been called.
	 *
	 * @throws VerificationException if verification fails
	 */
	public boolean verifyAllStubs() {

		// Verify all stubs by URL.
		this.wiremock.listAllStubMappings().getMappings().forEach(stub -> {
			final var requestPattern = stub.getRequest();
			this.wiremock.verify(
				anyRequestedFor(fromOneOf(requestPattern.getUrl(), requestPattern.getUrlPattern(), requestPattern.getUrlPath(), requestPattern.getUrlPathPattern())));
		});

		final var unmatchedRequests = this.wiremock.findAllUnmatchedRequests();
		if (!isEmpty(unmatchedRequests)) {
			final var unmatchedUrls = unmatchedRequests
				.stream()
				.map(LoggedRequest::getUrl)
				.toList();
			throw new AssertionError(format("The following requests was not matched: %s", unmatchedUrls));
		}

		return true;
	}
}
