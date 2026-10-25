package com.boltpe.creditscore.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VendorRateLimitStatusTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitMapsToHttp429() {
        var response = handler.handleVendorError(new VendorApiException("RATE_LIMITED", "Please try again later"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    void otherVendorFailureMapsToHttp503() {
        var response = handler.handleVendorError(new VendorApiException("CREDIT_SCORE_SERVICE_UNAVAILABLE", "Temporary issue"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
