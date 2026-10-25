package com.boltpe.creditscore.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.util.SensitiveDataEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreditScoreDebugControllerTest {
    @Test
    void decryptsStoredReportForMatchingOwner() {
        CreditScoreReportRepository repository = mock(CreditScoreReportRepository.class);
        SensitiveDataEncryptionService encryption = mock(SensitiveDataEncryptionService.class);
        CreditScoreReportEntity entity = new CreditScoreReportEntity();
        entity.setBoltpeResponseBody("encrypted");
        when(repository.findByRequestIdAndUserId("CSR-1", "user-1")).thenReturn(Optional.of(entity));
        when(encryption.decrypt("encrypted")).thenReturn("{\"score\":774}");

        var controller = new CreditScoreDebugController(repository, encryption, new ObjectMapper());
        var response = controller.decryptReport("user-1", new CreditScoreDebugController.DecryptReportRequest("CSR-1"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("CREDIT_SCORE_DATA_DECRYPTED", response.getBody().code());
        assertEquals("CSR-1", response.getBody().data().get("requestId"));
        assertNotNull(response.getBody().data().get("decryptedData"));
    }

    @Test
    void rejectsReportThatDoesNotBelongToUser() {
        CreditScoreReportRepository repository = mock(CreditScoreReportRepository.class);
        when(repository.findByRequestIdAndUserId("CSR-1", "other-user")).thenReturn(Optional.empty());
        var controller = new CreditScoreDebugController(repository, mock(SensitiveDataEncryptionService.class), new ObjectMapper());

        var decryptRequest = new CreditScoreDebugController.DecryptReportRequest("CSR-1");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.decryptReport("other-user", decryptRequest));
        assertEquals("Credit score report not found", ex.getMessage());
    }

    @Test
    void wrapsDecryptFailureWithoutLeakingCiphertext() {
        CreditScoreReportRepository repository = mock(CreditScoreReportRepository.class);
        SensitiveDataEncryptionService encryption = mock(SensitiveDataEncryptionService.class);
        CreditScoreReportEntity entity = new CreditScoreReportEntity();
        entity.setBoltpeResponseBody("encrypted");
        when(repository.findByRequestIdAndUserId("CSR-1", "user-1")).thenReturn(Optional.of(entity));
        when(encryption.decrypt("encrypted")).thenThrow(new IllegalStateException("crypto failure"));
        var controller = new CreditScoreDebugController(repository, encryption, new ObjectMapper());

        var decryptRequest = new CreditScoreDebugController.DecryptReportRequest("CSR-1");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> controller.decryptReport("user-1", decryptRequest));
        assertEquals("Unable to decrypt stored credit-score report", ex.getMessage());
        assertNotNull(ex.getCause());
    }
}
