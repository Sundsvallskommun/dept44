package se.sundsvall.dept44.configuration.feign.logging.util;

import feign.RequestLine;

public interface FeignClient {
	@RequestLine("GET /get/string")
	String getString();

	@RequestLine("GET /get/string")
	void getVoid();

	@RequestLine("POST /post/bad-request")
	String postBadRequest(String request);
}
