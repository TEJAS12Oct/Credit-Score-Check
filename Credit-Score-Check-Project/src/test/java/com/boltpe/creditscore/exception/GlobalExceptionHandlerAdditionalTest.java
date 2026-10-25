package com.boltpe.creditscore.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.core.MethodParameter;
import org.springframework.web.server.MissingRequestValueException;

import org.springframework.web.server.ServerWebInputException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerAdditionalTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void rateLimitedVendorReturns429() {
        var result = handler.handleVendorError(new VendorApiException("RATE_LIMITED", "Try later"));
        assertEquals(429, result.getStatusCode().value());
        assertEquals("RATE_LIMITED", result.getBody().code());
        assertEquals("Try later", result.getBody().message());
    }

    @Test void applicationErrorReturns400() {
        var result = handler.handleApplicationException(new ApplicationException("REPORT_EXPIRED", "Expired"));
        assertEquals(400, result.getStatusCode().value());
        assertEquals("REPORT_EXPIRED", result.getBody().code());
    }

    @Test void constraintViolationsDoNotLeakDetails() {
        var result = handler.handleConstraintViolationException(new ConstraintViolationException("sensitive value", Set.of()));
        assertEquals(400, result.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", result.getBody().code());
        assertEquals("Invalid request parameters.", result.getBody().message());
    }

    @Test
    void missingRequiredValueIsBadRequest() {
        MissingRequestValueException exception =
                new MissingRequestValueException(
                        "X-User-Id",
                        String.class,
                        "header",
                        null
                );

        var result = handler.handleMissingRequestValue(exception);

        assertEquals(400, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("INVALID_REQUEST", result.getBody().code());
    }

    @Test void malformedBodyIsBadRequest() {
        var result = handler.handleInvalidRequestBody(new ServerWebInputException("bad body"));
        assertEquals(400, result.getStatusCode().value());
        assertEquals("Invalid request body.", result.getBody().message());
    }

    @Test void mvcBeanValidationCollectsFieldErrors() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "mobile", "must have 10 digits"));
        binding.addError(new FieldError("request", "pan", "invalid PAN"));
        Method method = getClass().getDeclaredMethod("dummy", String.class);
        var parameter = new MethodParameter(method, 0);
        var response = handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(parameter, binding));
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("mobile: must have 10 digits"));
        assertTrue(response.getBody().message().contains("pan: invalid PAN"));
    }

    @Test void webFluxBeanValidationCollectsFieldErrors() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "otp", "required"));
        Method method = getClass().getDeclaredMethod("dummy", String.class);
        var response = handler.handleValidationException(new WebExchangeBindException(new MethodParameter(method, 0), binding));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("otp: required", response.getBody().message());
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // Reflection-only fixture: supplies a MethodParameter for binding exception tests.
    }
}
