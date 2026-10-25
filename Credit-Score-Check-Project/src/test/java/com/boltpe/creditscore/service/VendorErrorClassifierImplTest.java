package com.boltpe.creditscore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.service.impl.VendorErrorClassifierImpl;
import org.junit.jupiter.api.Test;

class VendorErrorClassifierImplTest {
    private final VendorErrorClassifierImpl classifier = new VendorErrorClassifierImpl();

    @Test
    void classifiesAuthenticationFailures() {
        assertEquals(VendorErrorClassifier.Type.AUTH, classifier.classify(401, null));
        assertEquals(VendorErrorClassifier.Type.AUTH,
                classifier.classify(200, new VendorSubmitOtpResponse(403, "Forbidden", null)));
    }

    @Test
    void classifiesRateLimitFailures() {
        assertEquals(VendorErrorClassifier.Type.RATE_LIMIT, classifier.classify(429, null));
        assertEquals(VendorErrorClassifier.Type.RATE_LIMIT,
                classifier.classify(200, new VendorSubmitOtpResponse(429, "Too many requests", null)));
    }

    @Test
    void classifiesInvalidOtpMessageVariants() {
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "Invalid OTP", null)));
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "OTP verification failed", null)));
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "OTP mismatch", null)));
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "Incorrect one-time password", null)));
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "Verification code is not valid", null)));
    }

    @Test
    void classifiesExpiredOtpMessageVariants() {
        assertEquals(VendorErrorClassifier.Type.EXPIRED_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "OTP expired", null)));
        assertEquals(VendorErrorClassifier.Type.EXPIRED_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "One-time password expiry", null)));
    }

    @Test
    void classifiesVendorInvalidDataAsInvalidRequest() {
        assertEquals(VendorErrorClassifier.Type.INVALID_REQUEST,
                classifier.classify(400, new VendorSubmitOtpResponse(
                        400, "Invalid Data: More or less than 10 characters", null)));
        assertEquals(VendorErrorClassifier.Type.INVALID_REQUEST,
                classifier.classify(400, new VendorSubmitOtpResponse(
                        400, "Invalid Data: Special characters", null)));
    }

    @Test
    void classifiesCreditsNotAvailable() {
        assertEquals(VendorErrorClassifier.Type.CREDITS_NOT_AVAILABLE,
                classifier.classify(402, new VendorSubmitOtpResponse(402, "Credits Not Available", null)));
        assertEquals(VendorErrorClassifier.Type.CREDITS_NOT_AVAILABLE,
                classifier.classify(200, new VendorSubmitOtpResponse(402, "Credits Not Available", null)));
    }

    @Test
    void classifiesDataNotFound() {
        assertEquals(VendorErrorClassifier.Type.DATA_NOT_FOUND,
                classifier.classify(404, new VendorSubmitOtpResponse(404, "Data Not Found", null)));
        assertEquals(VendorErrorClassifier.Type.DATA_NOT_FOUND,
                classifier.classify(200, new VendorSubmitOtpResponse(404, "Data Not Found", null)));
    }

    @Test
    void otpSpecific400TakesPrecedenceOverGenericInvalidRequest() {
        assertEquals(VendorErrorClassifier.Type.INVALID_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "Invalid OTP", null)));
        assertEquals(VendorErrorClassifier.Type.EXPIRED_OTP,
                classifier.classify(400, new VendorSubmitOtpResponse(400, "OTP expired", null)));
    }

    @Test
    void leavesServerAndUnknownFailuresTechnical() {
        assertEquals(VendorErrorClassifier.Type.TECHNICAL, classifier.classify(500, null));
        assertEquals(VendorErrorClassifier.Type.TECHNICAL, classifier.classify(501, null));
        assertEquals(VendorErrorClassifier.Type.TECHNICAL, classifier.classify(503, null));
        assertEquals(VendorErrorClassifier.Type.TECHNICAL,
                classifier.classify(418, new VendorSubmitOtpResponse(418, "Unknown failure", null)));
    }
}
