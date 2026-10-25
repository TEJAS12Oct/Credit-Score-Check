package com.boltpe.creditscore.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CreditScoreMetricsServiceImplTest {
    @Test
    void recordsSuccessfulAndFailedDurationsAndClampsNegativeDuration() {
        CreditScoreMetricsServiceImpl service = new CreditScoreMetricsServiceImpl();

        service.recordSubmitOtpDuration(125, true);
        service.recordSubmitOtpDuration(75, false);
        service.recordSubmitOtpDuration(-10, false);

        assertEquals(3, service.calls());
        assertEquals(2, service.failures());
        assertEquals(200, service.totalMillis());
    }
}
