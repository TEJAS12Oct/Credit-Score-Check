package com.boltpe.creditscore.validation;

import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.exception.RequestValidationException;
import org.springframework.stereotype.Component;

@Component
public class SubmitOtpRequestValidator {

    public void validate(String userId, SubmitOtpRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new RequestValidationException("AUTHENTICATION_REQUIRED", "Authenticated user is required");
        }
        if (request == null) {
            throw new RequestValidationException("INVALID_REQUEST", "Request body is required");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new RequestValidationException("REQUEST_ID_REQUIRED", "Request ID is required");
        }
        if (request.otp() == null || request.otp().isBlank()) {
            throw new RequestValidationException("OTP_REQUIRED", "OTP is required");
        }
        if (!request.otp().matches("^[0-9]{6}$")) {
            throw new RequestValidationException("INVALID_OTP_FORMAT", "OTP must contain exactly 6 digits");
        }
    }
}
