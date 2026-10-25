package com.boltpe.creditscore.client.kycflow.dto.request;

public record VendorSubmitOtpRequest(String token, String otp, String consent) {
}
