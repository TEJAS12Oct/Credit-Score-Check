package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.service.VendorErrorClassifier;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class VendorErrorClassifierImpl implements VendorErrorClassifier {

    @Override
    public Type classify(int httpStatus, VendorSubmitOtpResponse response) {
        Integer code = response == null ? null : response.code();

        if (httpStatus == 401 || httpStatus == 403
                || Integer.valueOf(401).equals(code) || Integer.valueOf(403).equals(code)) {
            return Type.AUTH;
        }
        if (httpStatus == 402 || Integer.valueOf(402).equals(code)) {
            return Type.CREDITS_NOT_AVAILABLE;
        }
        if (httpStatus == 404 || Integer.valueOf(404).equals(code)) {
            return Type.DATA_NOT_FOUND;
        }
        if (httpStatus == 429 || Integer.valueOf(429).equals(code)) {
            return Type.RATE_LIMIT;
        }

        String message = response == null || response.message() == null
                ? ""
                : response.message().trim().toLowerCase(Locale.ROOT);

        if (containsOtp(message) && containsAny(message,
                "expired", "expiry", "expire", "timed out", "timeout")) {
            return Type.EXPIRED_OTP;
        }

        // KYC providers do not always use the exact phrase "Invalid OTP". Treat only
        // OTP/code-specific verification failures as an invalid OTP. Do not classify
        // arbitrary HTTP 4xx responses as OTP failures because that would incorrectly
        // consume the user's verification attempts.
        if ((containsOtp(message) || message.contains("verification code"))
                && containsAny(message,
                        "invalid", "incorrect", "wrong", "failed", "failure",
                        "not valid", "mismatch", "does not match", "verification failed")) {
            return Type.INVALID_OTP;
        }

        // The vendor documents HTTP/code 400 for invalid input such as an
        // unexpected field length or unsupported special characters. Keep this
        // after the OTP-specific checks above so an invalid/expired OTP does not
        // get misclassified as a generic request-data error.
        if (httpStatus == 400 || Integer.valueOf(400).equals(code)) {
            return Type.INVALID_REQUEST;
        }

        return Type.TECHNICAL;
    }

    private boolean containsOtp(String message) {
        return message.contains("otp") || message.contains("one time password")
                || message.contains("one-time password");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
