package com.boltpe.creditscore.dto.response;

public record GetOtpResponse(
        String requestId,
        boolean otpSent,
        long resendAfterSeconds,
        int remainingResends) {
}
