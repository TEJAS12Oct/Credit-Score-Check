package com.boltpe.creditscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-score.vendor")
public record VendorProperties(
        String baseUrl,
        String generateOtpPath,
        String submitOtpPath,
        String clientId,
        String secretKey,
        int connectTimeoutMs,
        int responseTimeoutMs,
        int maxRetries) {
}
