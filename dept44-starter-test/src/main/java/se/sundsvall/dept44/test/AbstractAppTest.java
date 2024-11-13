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
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.ResourceUtils.getFile;

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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.javacrumbs.jsonunit.JsonAssert;
import net.javacrumbs.jsonunit.core.Option;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

public abstract class AbstractAppTest {

	private static final String FILES_DIR = "__files/";
	private static final String COMMON_MAPPING_DIR = "/common";
	private static final String MAPPING_DIRECTORY = "/mappings";
	private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
	private static final UriBuilder URI_BUILDER = new DefaultUriBuilderFactory().builder();
	private static final int DEFAULT_VERIFICATION_DELAY_IN_SECONDS = 5;
	private static final Class<?> DEFAULT_RESPONSE_TYPE = String.class;
	private static final MediaType DEFAULT_CONTENT_TYPE = APPLICATION_JSON;

	private final Logger logger = LoggerFactory.getLogger(getClass().getName());

	private boolean expectedResponseBodyIsNull;
	private int maxVerificationDelayInSeconds = DEFAULT_VERIFICATION_DELAY_IN_SECONDS;
	private MultiValueMap<String, Object> multipartBody;
	private String requestBody;
	private String expectedResponseBody;
	private byte[] expectedResponseBinary;
	private Class<?> expectedResponseType = DEFAULT_RESPONSE_TYPE;
	private String mappingPath;
	private String servicePath;
	private ResponseEntity<?> response;
	private String responseBody;
	private HttpHeaders responseHeaders;
	private HttpMethod method;
	private MediaType contentType = DEFAULT_CONTENT_TYPE;
	private HttpStatus expectedResponseStatus;
	private HttpHeaders expectedResponseHeaders;
	private Map<String, String> headerValues;
	private String testDirectoryPath;
	private String testCaseName;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected WireMockServer wiremock;

	public AbstractAppTest reset() {
		expectedResponseBodyIsNull = false;
		multipartBody = null;
		requestBody = null;
		expectedResponseBody = null;
		expectedResponseBinary = null;
		expectedResponseType = DEFAULT_RESPONSE_TYPE;
		mappingPath = null;
		servicePath = null;
		response = null;
		responseBody = null;
		responseHeaders = null;
		method = null;
		contentType = DEFAULT_CONTENT_TYPE;
		expectedResponseStatus = null;
		expectedResponseHeaders = null;
		headerValues = null;
		testDirectoryPath = null;
		testCaseName = null;

		return this;
	}

	public AbstractAppTest setupPaths() {
		// Fetch test case name.
		testCaseName = getTestMethodName();

		mappingPath = wiremock.getOptions().filesRoot().getPath();
		if (!mappingPath.endsWith("/")) {
			mappingPath += "/";
		}

		testDirectoryPath = "classpath:" + mappingPath + FILES_DIR + getTestMethodName() + FileSystems.getDefault()
			.getSeparator();

		return this;
	}

	public AbstractAppTest setupCall() {
		reset();
		initializeJsonAssert();
		setupPaths();

		wiremock.loadMappingsUsing(new JsonFileMappingsSource(
			new ClasspathFileSource(mappingPath + FILES_DIR + COMMON_MAPPING_DIR + MAPPING_DIRECTORY)));
		if (nonNull(testCaseName)) {
			wiremock.loadMappingsUsing(new JsonFileMappingsSource(
				new ClasspathFileSource(mappingPath + FILES_DIR + testCaseName + MAPPING_DIRECTORY)));
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
		if (isNull(headerValues)) {
			headerValues = new HashMap<>();
		}
		headerValues.put(key, value);
		return this;
	}

	/**
	 * Set expected response header.
	 *
	 * @param  expectedHeaderKey   the expected header key.
	 * @param  expectedHeaderValue the list of expected header values, as regular expressions.
	 * @return                     AbstractAppTest
	 */
	public AbstractAppTest withExpectedResponseHeader(final String expectedHeaderKey,
		final List<String> expectedHeaderValue) {
		if (isNull(expectedResponseHeaders)) {
			expectedResponseHeaders = new HttpHeaders();
		}
		expectedResponseHeaders.put(expectedHeaderKey, expectedHeaderValue);
		return this;
	}

	/**
	 * Method takes a JSON response string or a file name where the response can be
	 * read from.
	 *
	 * @param  expectedResponse raw json response string or filename where the response can be
	 *                          read from
	 * @return                  AbstractAppTest
	 */
	public AbstractAppTest withExpectedResponse(final String expectedResponse) {
		final var contentFromFile = fromTestFile(expectedResponse);
		if (nonNull(contentFromFile)) {
			expectedResponseBody = contentFromFile;
		} else {
			expectedResponseBody = expectedResponse;
		}

		return this;
	}

	/**
	 * Method takes a file name to a binary file.
	 *
	 * @param  expectedResponseFile the filename where the binary response can be read from.
	 * @return                      AbstractAppTest
	 * @throws IOException          if file can't be read.
	 */
	public AbstractAppTest withExpectedBinaryResponse(final String expectedResponseFile) throws IOException {
		final var file = ResourceUtils.getFile(testDirectoryPath + expectedResponseFile);
		expectedResponseBinary = Files.readAllBytes(file.toPath());
		expectedResponseType = byte[].class;

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

	public AbstractAppTest withServicePath(final Function<UriBuilder, URI> servicePathFunction) {
		servicePath = servicePathFunction.apply(URI_BUILDER).toString();
		return this;
	}

	/**
	 * Method adds options to be used when assertion of json is done, for example IGNORING_EXTRA_ARRAY_ITEMS.
	 * By default the test will compare arrays with option to ignore array order. If the need to use
	 * maximum strictness in JsonAssert - send in null or an empty list to just reset options to
	 * JsonAsserts default ones.
	 *
	 * @param  options list of options to use when doing the json assertion or null/empty list for resetting
	 *                 to JsonAssert defaults (strict comparison)
	 * @return         AbstractAppTest
	 */
	public AbstractAppTest withJsonAssertOptions(final List<Option> options) {
		// Reset to JsonAssert strict assertion options (removing option IGNORING_ARRAY_ORDER)
		JsonAssert.resetOptions();

		if (nonNull(options)) {
			// Set sent in assertion options
			if (options.size() == 1) {
				setOptions(options.getFirst());
			} else {
				setOptions(options.getFirst(), options.subList(1, options.size()).toArray(new Option[0]));
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
	 * @return         AbstractAppTest
	 */
	public AbstractAppTest withRequest(final String request) {
		final var contentFromFile = fromTestFile(request);
		if (nonNull(contentFromFile)) {
			requestBody = contentFromFile;
		} else {
			requestBody = request;
		}
		return this;
	}

	/**
	 * Method replaces sections in request matching sent in string with sent in replacement string.
	 * Observe that the withRequest method must be called before for this method to have any effect.
	 * 
	 * @param  matchingString    the string to match in request body
	 * @param  replacementString the string to replace with
	 * @return                   AbstractAppTest
	 */
	public AbstractAppTest withRequestReplacement(final String matchingString, final String replacementString) {
		if (allNotNull(requestBody, matchingString, replacementString)) {
			requestBody = StringUtils.replace(requestBody, matchingString, replacementString);
		}
		return this;
	}

	/**
	 * Method takes a file that will be added to the multipart body.
	 *
	 * @param  parameterName         the name of the multipart parameter.
	 * @param  fileName              to be added to the request as a multipart, the method will look for the file in the
	 *                               current
	 *                               test-case directory.
	 * @return                       AbstractAppTest
	 * @throws FileNotFoundException if the file doesn't exist
	 */
	public AbstractAppTest withRequestFile(final String parameterName, final String fileName)
		throws FileNotFoundException {
		return withRequestFile(parameterName, getFile(testDirectoryPath + fileName));
	}

	/**
	 * Method takes a file that will be added to the multipart body.
	 *
	 * @param  parameterName the name of the multipart parameter.
	 * @param  file          to be added to the request as a multipart.
	 * @return               AbstractAppTest
	 */
	public AbstractAppTest withRequestFile(final String parameterName, final File file) {
		if (isNull(multipartBody)) {
			multipartBody = new LinkedMultiValueMap<>();
		}

		multipartBody.add(parameterName, new FileSystemResource(file));

		return this;
	}

	/**
	 * Method takes a MultiValueMap that will set the multipart body.
	 * If you have added any parts to the multipartbody before a call to this method, these parts will be lost.
	 *
	 * @param  multiPartBody the multipartbody (as a MultiValueMap).
	 * @return               AbstractAppTest
	 * @see                  org.springframework.http.client.MultipartBodyBuilder for instruction on how to create a
	 *                       suitable MultiValueMap.
	 */
	@SuppressWarnings("unchecked")
	public AbstractAppTest withRequest(final MultiValueMap<?, ?> multiPartBody) {
		this.multipartBody = (MultiValueMap<String, Object>) multiPartBody;
		return this;
	}

	/**
	 * Set max verification delay in seconds.
	 *
	 * I.e. the maximum time to spend while verifying a condition.
	 *
	 * @param  maxVerificationDelayInSeconds the number of seconds that the verification logic will try before failing.
	 * @return                               AbstractAppTest
	 */
	public AbstractAppTest withMaxVerificationDelayInSeconds(final int maxVerificationDelayInSeconds) {
		this.maxVerificationDelayInSeconds = maxVerificationDelayInSeconds;
		return this;
	}

	public AbstractAppTest sendRequestAndVerifyResponse() {
		return sendRequest().verifyStubs();
	}

	public AbstractAppTest sendRequest() {
		logger.info(getTestMethodName());

		final var requestEntity = nonNull(multipartBody) ? restTemplateRequest(contentType, multipartBody)
			: restTemplateRequest(contentType, requestBody);

		// Call service and fetch response.
		response = restTemplate.exchange(servicePath, method, requestEntity, expectedResponseType);
		responseBody = nonNull(response.getBody()) ? String.valueOf(response.getBody()) : null;
		responseHeaders = response.getHeaders();

		if (nonNull(expectedResponseHeaders)) {
			expectedResponseHeaders.entrySet().stream().forEach(expectedHeader -> {
				assertThat(response.getHeaders()).containsKey(expectedHeader.getKey());
				assertThat(response.getHeaders().getValuesAsList(expectedHeader.getKey()))
					.allMatch(actualHeaderValue -> expectedHeader.getValue().stream()
						.allMatch(expectedHeaderValue -> equalsIgnoreCase(expectedHeaderValue, actualHeaderValue) ||
							Pattern.matches(expectedHeaderValue, actualHeaderValue)));
			});
		}
		assertThat(response.getStatusCode()).isEqualTo(expectedResponseStatus);
		if (nonNull(expectedResponseBody)) {
			final var responseContentType = response.getHeaders().getContentType();
			if (nonNull(responseContentType) && responseContentType.isPresentIn(List.of(APPLICATION_JSON,
				APPLICATION_PROBLEM_JSON))) {
				// Compare as JSON
				assertJsonEquals(expectedResponseBody, responseBody);
			} else {
				// Compare as text
				assertThat(expectedResponseBody).isEqualToIgnoringWhitespace(responseBody);
			}
		}
		if (nonNull(expectedResponseBinary)) {
			// Compare as Binary
			assertThat(expectedResponseBinary).isEqualTo(response.getBody());
		}
		if (expectedResponseBodyIsNull) {
			assertThat(responseBody).isNull();
		}

		return this;
	}

	public AbstractAppTest verifyStubs() {
		await()
			.atMost(maxVerificationDelayInSeconds, SECONDS)
			.pollDelay(0, SECONDS)
			.pollInterval(1, SECONDS)
			.ignoreExceptions()
			.until(this::verifyAllStubs);

		wiremock.resetAll();

		return this;
	}

	/**
	 * Returns the response body mapped to the provided class.
	 *
	 * @param  <T>   Response type.
	 * @param  clazz the class to map the response body to
	 * @return       the mapped response
	 */
	public <T> T getResponseBody(final Class<T> clazz) throws JsonProcessingException, ClassNotFoundException {
		return andReturnBody(clazz);
	}

	/**
	 * Returns the response body mapped to the provided type reference.
	 *
	 * @param  <T>           Response type.
	 * @param  typeReference the type reference to map the response body to
	 * @return               the mapped response
	 */
	public <T> T getResponseBody(final TypeReference<T> typeReference) throws JsonProcessingException {
		return andReturnBody(typeReference);
	}

	/**
	 * Method returns the received server response mapped to the sent in class.
	 *
	 * @param  <T>                     Response type.
	 * @param  clazz                   class to map response to
	 * @return                         response mapped to sent in class type
	 * @throws JsonProcessingException if JSON-processing fails
	 * @throws ClassNotFoundException  if class is not found
	 */
	public <T> T andReturnBody(final Class<T> clazz) throws JsonProcessingException, ClassNotFoundException {
		return clazz.cast(JSON_MAPPER.readValue(responseBody, forName(clazz.getName())));
	}

	/**
	 * Returns the received server response mapped to the given type reference.
	 *
	 * @param  <T>           Response type.
	 * @param  typeReference the type reference to map response to
	 * @return               response mapped to given type reference
	 */
	public <T> T andReturnBody(final TypeReference<T> typeReference) throws JsonProcessingException {
		return JSON_MAPPER.readValue(responseBody, typeReference);
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

	public String getTestDirectoryPath() {
		return this.testDirectoryPath;
	}

	private HttpEntity<Object> restTemplateRequest(final MediaType mediaType, Object body) {
		final var httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(mediaType);
		httpHeaders.add("x-test-case", getClass().getSimpleName() + "." + getTestMethodName());
		if (!isEmpty(headerValues)) {
			headerValues.forEach(httpHeaders::add);
		}
		return new HttpEntity<>(body, httpHeaders);
	}

	protected String fromTestFile(final String fileName) {
		return fromFile(testDirectoryPath + fileName);
	}

	private String fromFile(final String filePath) {
		try {
			return readString(getFile(filePath).toPath());
		} catch (final IOException e) {
			return null;
		}
	}

	private String getTestMethodName() {
		return Arrays.stream(Thread.currentThread().getStackTrace())
			.map(StackTraceElement::getMethodName)
			.filter(methodName -> methodName.startsWith("test"))
			.findFirst()
			.orElseThrow(() -> new UnsupportedOperationException(
				"Could not find method name! Test method must start with 'test'"));
	}

	private void initializeJsonAssert() {
		JsonAssert.setTolerance(0); // Activates mathematical equivalence (i.e. 1.0 == 1.000)
		JsonAssert.setOptions(Option.IGNORING_ARRAY_ORDER);
	}

	/**
	 * Verifies that all setup stubs setup have been called.
	 *
	 * @return                       true if verification succeeds.
	 * @throws VerificationException if verification fails
	 */
	public boolean verifyAllStubs() {
		// Verify all stubs by URL.
		wiremock.listAllStubMappings().getMappings().forEach(stub -> {
			final var requestPattern = stub.getRequest();
			wiremock.verify(
				anyRequestedFor(fromOneOf(requestPattern.getUrl(), requestPattern.getUrlPattern(), requestPattern
					.getUrlPath(), requestPattern.getUrlPathPattern())));
		});

		final var unmatchedRequests = wiremock.findAllUnmatchedRequests();
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
