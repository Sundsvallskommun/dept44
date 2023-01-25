package se.sundsvall.dept44.configuration.feign.logging;

import java.nio.charset.Charset;
import java.util.Optional;

import javax.annotation.Nullable;

import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;

import feign.Response;

/**
 * This package and all its classes exists because FeignLogbookLogger doesn't handle responses where body 
 * is null when converting response.body() to byte array in method logAndRebufferResponse.
 * 
 * This class is a copy of class RemoteResponse in package org.zalando.logbook.openfeign.
 *  
 * TODO: Remove this package and all its classes and change Feign configuration to use the logger provided
 * by feign logbook library when https://github.com/zalando/logbook/pull/1222 is released.
 */
final class RemoteResponse implements HttpResponse {
    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Charset charset;
    private boolean withBody = false;

    public static RemoteResponse create(Response response, byte[] body) {
    	return new RemoteResponse (
	    	response.status(),
	    	HeaderUtils.toLogbookHeaders(response.headers()),
	    	body,
	    	response.charset());
    }

    private RemoteResponse(int status, HttpHeaders headers, byte[] body, Charset charset) {
    	this.status = status;
    	this.headers = headers;
    	this.body = body;
    	this.charset = charset;
    }
    
    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getProtocolVersion() {
        // feign doesn't support HTTP/2, their own toString looks like this:
        // builder.append(httpMethod).append(' ').append(url).append(" HTTP/1.1\n");
        return "HTTP/1.1";
    }

    @Override
    public Origin getOrigin() {
        return Origin.REMOTE;
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
        return charset;
    }

    @Override
    public HttpResponse withBody() {
        withBody = true;
        return this;
    }

    @Override
    public RemoteResponse withoutBody() {
        withBody = false;
        return this;
    }

    @Override
    public byte[] getBody() {
        return withBody && body != null ? body : new byte[0];
    }
}
