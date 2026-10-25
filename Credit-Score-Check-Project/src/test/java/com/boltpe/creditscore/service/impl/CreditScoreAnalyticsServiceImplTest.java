package com.boltpe.creditscore.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class CreditScoreAnalyticsServiceImplTest {
    @Test
    void recordsSanitizedAnalyticsEvent() {
        CreditScoreAnalyticsServiceImpl service = new CreditScoreAnalyticsServiceImpl();
        assertDoesNotThrow(() -> service.record("cs_otp_verified", "CSR-1", "user-1", "SUCCESS"));
    }
}
