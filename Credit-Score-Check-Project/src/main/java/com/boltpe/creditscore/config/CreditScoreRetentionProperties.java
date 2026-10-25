package com.boltpe.creditscore.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="credit-score.retention")
public record CreditScoreRetentionProperties(boolean enabled, long reportDays) {}
