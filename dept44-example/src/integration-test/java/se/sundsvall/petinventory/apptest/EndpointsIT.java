package se.sundsvall.petinventory.apptest;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.dept44.requestid.RequestId.HEADER_NAME;
import static se.sundsvall.petinventory.apptest.Constants.REG_EXP_VALID_UUID;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.petinventory.Application;

/**
 * EndpointsIT tests.
 */
@WireMockAppTestSuite(files = "classpath:/EndpointsIT/", classes = Application.class)
class EndpointsIT extends AbstractAppTest {

	@Test
	void test01_apiDocs() {

		// Call
		setupCall()
			.withServicePath("/api-docs")
			.withHeader(ACCEPT, APPLICATION_JSON_VALUE)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test02_health() {

		// Call
		setupCall()
			.withServicePath("/actuator/health")
			.withHeader(ACCEPT, APPLICATION_JSON_VALUE)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test03_info() {

		// Call
		setupCall()
			.withServicePath("/actuator/info")
			.withHeader(ACCEPT, APPLICATION_JSON_VALUE)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test04_heapdump() {

		// Call
		setupCall()
			.withServicePath("/actuator/heapdump")
			.withHeader(ACCEPT, APPLICATION_JSON_VALUE)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(NOT_FOUND)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test05_threaddump() {

		// Call
		setupCall()
			.withServicePath("/actuator/threaddump")
			.withHeader(ACCEPT, APPLICATION_JSON_VALUE)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(NOT_FOUND)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}
}
