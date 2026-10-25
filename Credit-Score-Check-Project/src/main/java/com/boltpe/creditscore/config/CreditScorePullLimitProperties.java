package com.boltpe.creditscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-score.pull-limit")
public record CreditScorePullLimitProperties(boolean enabled, long hours) {}
