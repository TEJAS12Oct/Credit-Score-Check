package com.boltpe.creditscore.service;
public interface CreditScoreAnalyticsService {
    void record(String event, String requestId, String userId, String outcome);
}
