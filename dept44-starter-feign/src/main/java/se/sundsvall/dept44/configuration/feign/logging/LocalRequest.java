package se.sundsvall.dept44.configuration.feign.logging;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.Nullable;

import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Origin;

import feign.Request;

/**
 * This package and all its classes exists because FeignLogbookLogger doesn't handle responses where body
 * is null when converting response.body() to byte array in method logAndRebufferResponse.
 * 
 * This class is a copy of class LocalRequest in package org.zalando.logbook.openfeign.
 * 
 * TODO: Remove this package and all its classes and change Feign configuration to use the logger provided
 * by feign logbook library when https://github.com/zalando/logbook/pull/1222 is released.
 */
final class LocalRequest implements HttpRequest {
	private final URI uri;
	private final Request.HttpMethod httpMethod;
	private final HttpHeaders headers;
	private final byte[] body;
	private final Charset charset;
	private boolean withBody = false;

	public static LocalRequest create(Request request) {
		return new LocalRequest(
			URI.create(getRequestUrl(request)),
			request.httpMethod(),
			HeaderUtils.toLogbookHeaders(request.headers()),
			request.body(),
			request.charset());
	}

	private LocalRequest(URI uri, Request.HttpMethod httpMethod, HttpHeaders headers, byte[] body, Charset charset) {
		this.uri = uri;
		this.httpMethod = httpMethod;
		this.headers = headers;
		this.body = body;
		this.charset = charset;
	}

	@Override
	public String getRemote() {
		return "localhost";
	}

	@Override
	public String getMethod() {
		return httpMethod.toString();
	}

	@Override
	public String getScheme() {
		return uri.getScheme() == null ? "" : uri.getScheme();
	}

	@Override
	public String getHost() {
		return uri.getHost() == null ? "" : uri.getHost();
	}

	@Override
	public Optional<Integer> getPort() {
		return Optional.of(uri).map(URI::getPort).filter(p -> p != -1);
	}

	@Override
	public String getPath() {
		return uri.getPath() == null ? "" : uri.getPath();
	}

	@Override
	public String getQuery() {
		return uri.getQuery() == null ? "" : uri.getQuery();
	}

	@Override
	public HttpRequest withBody() {
		withBody = true;
		return this;
	}

	@Override
	public HttpRequest withoutBody() {
		withBody = false;
		return this;
	}

	@Override
	public String getProtocolVersion() {
		// feign doesn't support HTTP/2, their own toString looks like this:
		// builder.append(httpMethod).append(' ').append(url).append(" HTTP/1.1\n");
		return "HTTP/1.1";
	}

	@Override
	public Origin getOrigin() {
		return Origin.LOCAL;
	}

	@Override
	public HttpHeaders getHeaders() {
		return headers;
	}

	@Nullable
	@Override
	public String getContentType() {
		return Optional.ofNullable(headers.get("Content-Type"))
			.flatMap(ct -> ct.stream().findFirst())
			.orElse(null);
	}

	@Override
	public Charset getCharset() {
		return charset == null ? StandardCharsets.UTF_8 : charset;
	}

	@Override
	public byte[] getBody() {
		return withBody && body != null ? body : new byte[0];
	}

	private static String getRequestUrl(Request request) {
		return request.url().replace(" ", "%20");
	}
}
