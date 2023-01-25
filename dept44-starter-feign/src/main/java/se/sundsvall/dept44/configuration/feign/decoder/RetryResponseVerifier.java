package se.sundsvall.dept44.configuration.feign.decoder;

import feign.Response;

public interface RetryResponseVerifier {

    boolean shouldReturnRetryableException(Response response);
    String getMessage();
}
