package com.boltpe.creditscore.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.exception.RequestValidationException;
import org.junit.jupiter.api.Test;

class SubmitOtpRequestValidatorTest {
    private final SubmitOtpRequestValidator validator = new SubmitOtpRequestValidator();

    @Test void acceptsValidRequest() { validator.validate("user-1", new SubmitOtpRequest("CSR-1", "123456")); }
    @Test void rejectsMissingUser() { assertCode("AUTHENTICATION_REQUIRED", () -> validator.validate(" ", new SubmitOtpRequest("CSR-1", "123456"))); }
    @Test void rejectsNullRequest() { assertCode("INVALID_REQUEST", () -> validator.validate("user-1", null)); }
    @Test void rejectsMissingRequestId() { assertCode("REQUEST_ID_REQUIRED", () -> validator.validate("user-1", new SubmitOtpRequest(" ", "123456"))); }
    @Test void rejectsMissingOtp() { assertCode("OTP_REQUIRED", () -> validator.validate("user-1", new SubmitOtpRequest("CSR-1", " "))); }
    @Test void rejectsShortOtp() { assertCode("INVALID_OTP_FORMAT", () -> validator.validate("user-1", new SubmitOtpRequest("CSR-1", "12345"))); }
    @Test void rejectsLongOtp() { assertCode("INVALID_OTP_FORMAT", () -> validator.validate("user-1", new SubmitOtpRequest("CSR-1", "1234567"))); }
    @Test void rejectsAlphaOtp() { assertCode("INVALID_OTP_FORMAT", () -> validator.validate("user-1", new SubmitOtpRequest("CSR-1", "12A456"))); }

    private void assertCode(String code, Runnable runnable) {
        RequestValidationException ex = assertThrows(RequestValidationException.class, runnable::run);
        assertEquals(code, ex.getCode());
    }
}
