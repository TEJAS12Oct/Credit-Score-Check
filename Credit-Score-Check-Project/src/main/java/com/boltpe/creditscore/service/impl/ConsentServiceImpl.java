package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.entity.ConsentAuditEntity;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import com.boltpe.creditscore.repository.ConsentAuditRepository;
import com.boltpe.creditscore.service.ConsentService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentServiceImpl implements ConsentService {

    private static final String CONSENT_VERSION = "v1";

    private final ConsentAuditRepository repository;

    public ConsentServiceImpl(ConsentAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String requestId, String userId) {
        Instant now = Instant.now();

        ConsentAuditEntity consent = ConsentAuditEntity.builder()
                .requestId(requestId)
                .userId(userId)
                .consentGiven(true)
                .consentText(GetOtpVendorMapper.CONSENT_TEXT)
                .consentVersion(CONSENT_VERSION)
                .consentedAt(now)
                .createdAt(now)
                .build();

        repository.save(consent);
    }
}
