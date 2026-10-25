package com.boltpe.creditscore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubmitOtpRequest(
        @NotBlank(message = "Request ID is required") String requestId,
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain exactly 6 digits") String otp) {
}
