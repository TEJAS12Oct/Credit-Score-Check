package com.boltpe.creditscore.service;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
public interface VendorErrorClassifier {
 enum Type {
     INVALID_OTP,
     EXPIRED_OTP,
     AUTH,
     RATE_LIMIT,
     INVALID_REQUEST,
     CREDITS_NOT_AVAILABLE,
     DATA_NOT_FOUND,
     TECHNICAL
 }
 Type classify(int httpStatus, VendorSubmitOtpResponse response);
}
