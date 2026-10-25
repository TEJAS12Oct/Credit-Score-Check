package com.boltpe.creditscore.service;

import com.boltpe.creditscore.entity.ConsentAuditEntity;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import com.boltpe.creditscore.repository.ConsentAuditRepository;
import com.boltpe.creditscore.service.impl.ConsentServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsentServiceImplTest {

    @Test
    void consentIsPersistedWithRequiredText() {
        ConsentAuditRepository repository = mock(ConsentAuditRepository.class);
        ConsentServiceImpl service = new ConsentServiceImpl(repository);

        service.record("CS-1", "user-1");

        ArgumentCaptor<ConsentAuditEntity> captor = ArgumentCaptor.forClass(ConsentAuditEntity.class);
        verify(repository).save(captor.capture());

        ConsentAuditEntity saved = captor.getValue();
        assertEquals("CS-1", saved.getRequestId());
        assertEquals("user-1", saved.getUserId());
        assertTrue(saved.isConsentGiven());
        assertEquals(GetOtpVendorMapper.CONSENT_TEXT, saved.getConsentText());
        assertEquals("v1", saved.getConsentVersion());
        assertNotNull(saved.getConsentedAt());
        assertNotNull(saved.getCreatedAt());
    }
}
