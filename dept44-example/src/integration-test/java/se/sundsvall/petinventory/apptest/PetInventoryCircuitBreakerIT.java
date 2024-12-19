package se.sundsvall.petinventory.apptest;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static se.sundsvall.dept44.requestid.RequestId.HEADER_NAME;
import static se.sundsvall.petinventory.apptest.Constants.REG_EXP_VALID_UUID;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.petinventory.Application;

/**
 * PetInventoryCircuitBreakerIT tests.
 */
@WireMockAppTestSuite(files = "classpath:/PetInventoryCircuitBreakerIT/", classes = Application.class)
@Sql({
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-it.sql"
})
class PetInventoryCircuitBreakerIT extends AbstractAppTest {

	@Test
	void test01_triggerCircuitBreaker() {

		// Verify that health is "UP".
		setupCall()
			.withServicePath("/actuator/health")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withJsonAssertOptions(List.of(IGNORING_EXTRA_FIELDS))
			.withExpectedResponse("health-ok-response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequest();

		// Given the current defaults, we must call the faulty integration 5 times until the circuit opens.
		for (int i = 0; i < 5; i++) {
			setupCall()
				.withServicePath("/pet-inventory-items")
				.withHttpMethod(GET)
				.withExpectedResponseStatus(BAD_GATEWAY)
				.withExpectedResponse("error-response.json")
				.sendRequestAndVerifyResponse();
		}

		// Call one more time to verify that the circuit is open.
		setupCall()
			.withServicePath("/pet-inventory-items")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(INTERNAL_SERVER_ERROR)
			.withExpectedResponse("circuitbreaker-open-response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequest();

		// Verify that health is "RESTRICTED".
		setupCall()
			.withServicePath("/actuator/health")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withJsonAssertOptions(List.of(IGNORING_EXTRA_FIELDS))
			.withExpectedResponse("health-restricted-response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequest();
	}
}
