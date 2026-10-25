package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.service.CreditScoreDataLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger delegates to a separate Spring transactional service proxy. */
@Component
public class CreditScoreCleanupScheduler {
    private final CreditScoreDataLifecycleService lifecycle;

    public CreditScoreCleanupScheduler(CreditScoreDataLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(cron = "${credit-score.retention.cleanup-cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        lifecycle.deleteExpiredReports();
    }
}
