package se.sundsvall.dept44.configuration.feign.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.zalando.logbook.HttpHeaders;

/**
 * This package and all its classes exists because FeignLogbookLogger doesn't handle responses where body
 * is null when converting response.body() to byte array in method logAndRebufferResponse.
 * 
 * This class is a copy of class HeaderUtils in package org.zalando.logbook.openfeign.
 * 
 * TODO: Remove this package and all its classes and change Feign configuration to use the logger provided
 * by feign logbook library when https://github.com/zalando/logbook/pull/1222 is released.
 */
class HeaderUtils {

	private HeaderUtils() {}

	/**
	 * Convert Feign headers to Logbook-compatible format
	 *
	 * @param feignHeaders original headers
	 * @return Logbook headers
	 */
	static HttpHeaders toLogbookHeaders(Map<String, Collection<String>> feignHeaders) {
		Map<String, List<String>> convertedHeaders = new HashMap<>();
		for (Map.Entry<String, Collection<String>> header : feignHeaders.entrySet()) {
			convertedHeaders.put(header.getKey(), new ArrayList<>(header.getValue()));
		}
		return HttpHeaders.of(convertedHeaders);
	}
}
