package com.boltpe.creditscore.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returns503ForVendorFailure() {
        var response = handler.handleVendorError(
                new VendorApiException("VENDOR_UNAVAILABLE", "Temporary failure"));

        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().success());
        assertEquals("VENDOR_UNAVAILABLE", response.getBody().code());
    }

    @Test
    void returns400ForBusinessValidation() {
        var response = handler.handleBusinessValidation(
                new RequestValidationException("INVALID_CONSENT", "Valid consent text is required"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_CONSENT", response.getBody().code());
    }

    @Test
    void returns500ForUnexpectedFailureWithoutLeakingMessage() {
        var response = handler.handleUnexpected(new RuntimeException("database-password"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
        assertFalse(response.getBody().message().contains("database-password"));
    }
}
