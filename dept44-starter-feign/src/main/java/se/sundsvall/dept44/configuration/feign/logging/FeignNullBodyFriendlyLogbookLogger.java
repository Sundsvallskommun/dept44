package se.sundsvall.dept44.configuration.feign.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Logbook.ResponseProcessingStage;

import feign.Logger;
import feign.Request;
import feign.Response;

/**
 * This package and all its classes exists because FeignLogbookLogger doesn't handle responses where body 
 * is null when converting response.body() to byte array in method logAndRebufferResponse.
 * 
 * This class is a copy of class FeignLogbookLogger in package org.zalando.logbook.openfeign with the 
 * extension to check if body is null before calling asInputStream() on it.
 *  
 * TODO: Remove this package and all its classes and change Feign configuration to use the logger provided
 * by feign logbook library when https://github.com/zalando/logbook/pull/1222 is released.
 */
public final class FeignNullBodyFriendlyLogbookLogger extends Logger {
    private final Logbook logbook;
    // Feign is blocking, so there is no context switch between request and response
    private final ThreadLocal<ResponseProcessingStage> stage = new ThreadLocal<>();

    public FeignNullBodyFriendlyLogbookLogger(Logbook logbook) {
    	this.logbook = logbook;
    }
    
    @Override
    protected void log(String configKey, String format, Object... args) {
        /* no-op, logging is delegated to logbook */
    }

    @Override
    protected void logRetry(String configKey, Level logLevel) {
        /* no-op, logging is delegated to logbook */
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        final HttpRequest httpRequest = LocalRequest.create(request);
        try {
            ResponseProcessingStage processingStage = logbook.process(httpRequest).write();
            stage.set(processingStage);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) {
        try {
            // Logbook will consume body stream, making it impossible to read it again
            // read body here and create new response based on byte array instead, but check if body is present before
        	// trying to read as inputstream.
            byte[] body = Objects.isNull(response.body()) ? null : ByteStreams.toByteArray(response.body().asInputStream());

            final HttpResponse httpResponse = RemoteResponse.create(response, body);
            stage.get().process(httpResponse).write();

            // create a copy of response to provide consumed body
            return Response.builder()
                    .status(response.status())
                    .request(response.request())
                    .reason(response.reason())
                    .headers(response.headers())
                    .body(body)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
