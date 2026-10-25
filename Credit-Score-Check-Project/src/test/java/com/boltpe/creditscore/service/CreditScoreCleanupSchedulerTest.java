package com.boltpe.creditscore.service;

import com.boltpe.creditscore.service.impl.CreditScoreCleanupScheduler;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CreditScoreCleanupSchedulerTest {
    @Test void scheduledCleanupDelegatesToTransactionalService() {
        CreditScoreDataLifecycleService lifecycle = mock(CreditScoreDataLifecycleService.class);
        new CreditScoreCleanupScheduler(lifecycle).scheduledCleanup();
        verify(lifecycle).deleteExpiredReports();
        verifyNoMoreInteractions(lifecycle);
    }
}
