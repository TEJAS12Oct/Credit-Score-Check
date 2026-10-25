package com.boltpe.creditscore.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boltpe.creditscore.service.CreditScoreDataLifecycleService;
import org.junit.jupiter.api.Test;

class CreditScoreDataControllerTest {

    @Test
    void deletesCurrentUsersCreditScoreData() {
        CreditScoreDataLifecycleService lifecycleService = mock(CreditScoreDataLifecycleService.class);
        when(lifecycleService.withdrawConsentAndDeleteCachedReports("user-1")).thenReturn(2L);
        CreditScoreDataController controller = new CreditScoreDataController(lifecycleService);

        var response = controller.deleteCreditScoreData("user-1");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals("CREDIT_SCORE_DATA_DELETED", response.getBody().code());
        assertEquals("Credit score data deleted successfully", response.getBody().message());
        verify(lifecycleService).withdrawConsentAndDeleteCachedReports("user-1");
    }
}
