package se.sundsvall.petinventory.apptest;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.OK;
import static se.sundsvall.dept44.requestid.RequestId.HEADER_NAME;
import static se.sundsvall.petinventory.apptest.Constants.REG_EXP_VALID_UUID;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
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
@AutoConfigureTestRestTemplate
class PetInventoryCircuitBreakerIT extends AbstractAppTest {

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void resetCircuitBreaker() {
		// Reset all circuit breakers to ensure a clean state for each test
		circuitBreakerRegistry.getAllCircuitBreakers()
			.forEach(CircuitBreaker::reset);
	}

	@Test
	void test01_triggerCircuitBreaker() {

		// Verify circuit breaker is CLOSED using the /actuator/circuitbreakers endpoint
		// Note: In Spring Boot 4, circuitBreakers are not included in /actuator/health due to
		// Resilience4j compatibility issues. Using a dedicated endpoint instead.
		setupCall()
			.withServicePath("/actuator/circuitbreakers")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withJsonAssertOptions(List.of(IGNORING_EXTRA_FIELDS))
			.withExpectedResponse("circuitbreaker-closed-response.json")
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
			.withExpectedResponseStatus(SERVICE_UNAVAILABLE)
			.withExpectedResponse("circuitbreaker-open-error-response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequest();

		// Verify circuit breaker is OPEN using the /actuator/circuitbreakers endpoint
		setupCall()
			.withServicePath("/actuator/circuitbreakers")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withJsonAssertOptions(List.of(IGNORING_EXTRA_FIELDS))
			.withExpectedResponse("circuitbreaker-open-response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequest();
	}
}
