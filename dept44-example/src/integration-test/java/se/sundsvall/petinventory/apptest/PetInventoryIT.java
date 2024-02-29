package se.sundsvall.petinventory.apptest;

import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static se.sundsvall.dept44.requestid.RequestId.HEADER_NAME;
import static se.sundsvall.petinventory.apptest.Constants.REG_EXP_VALID_UUID;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.petinventory.Application;

/**
 * PetInventoryIT tests.
 */
@WireMockAppTestSuite(files = "classpath:/PetInventoryIT/", classes = Application.class)
@Sql({
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-it.sql"
})
class PetInventoryIT extends AbstractAppTest {

	@Test
	void test01_getPetInventoryList() {

		// Call
		setupCall()
			.withServicePath("/pet-inventory-items")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test02_getPetInventoryItem() {

		// Call
		setupCall()
			.withServicePath("/pet-inventory-items/3")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test03_getPetInventoryItemNotFound() {

		// Call
		setupCall()
			.withServicePath("/pet-inventory-items/666")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(NOT_FOUND)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test04_getPetInventoryItemSomethingWentWrong() {

		// Call
		setupCall()
			.withServicePath("/pet-inventory-items/777")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(BAD_GATEWAY)
			.withExpectedResponse("response.json")
			.withExpectedResponseHeader(HEADER_NAME, List.of(REG_EXP_VALID_UUID))
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test05_postPetImage() throws IOException {

		// Call
		final var location = setupCall()
			.withServicePath("/pet-inventory-items/5/images")
			.withRequestFile("file", "request/dept44.jpg")
			.withContentType(MULTIPART_FORM_DATA)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(CREATED)
			.withExpectedResponseHeader(LOCATION, List.of("^/pet-inventory-items/(\\d+)/images/(\\d+)$"))
			.sendRequestAndVerifyResponse().getResponseHeaders().getLocation();

		// Call
		setupCall()
			.withServicePath(location.getPath())
			.withHttpMethod(GET)
			.withExpectedBinaryResponse("response/dept44.jpg")
			.withExpectedResponseStatus(OK)
			.sendRequestAndVerifyResponse();
	}
}
