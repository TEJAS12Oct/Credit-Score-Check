package com.boltpe.creditscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-score")
public record GetOtpProperties(Otp otp) {
    public record Otp(long resendCooldownSeconds, int maxOtpSends, long sessionValiditySeconds,
                      int maxVerificationAttempts, long verificationLockSeconds) {
    }
}
