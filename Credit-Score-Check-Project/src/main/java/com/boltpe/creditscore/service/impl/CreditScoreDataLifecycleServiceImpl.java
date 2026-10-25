package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.config.CreditScoreRetentionProperties;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreDataLifecycleService;

import java.time.Instant;
import java.io.IOException;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import java.util.List;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.boltpe.creditscore.repository.GetOtpRequestRepository;
import com.boltpe.creditscore.repository.VendorApiLogRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditScoreDataLifecycleServiceImpl implements CreditScoreDataLifecycleService {
    private final CreditScoreReportRepository reports;
    private final GetOtpRequestRepository otpRequests;
    private final VendorApiLogRepository vendorLogs;
    private final CreditScoreRetentionProperties properties;
    private final CreditScorePdfStorageService storage;

    public CreditScoreDataLifecycleServiceImpl(CreditScoreReportRepository reports, CreditScoreRetentionProperties properties,
            CreditScorePdfStorageService storage, GetOtpRequestRepository otpRequests,
            VendorApiLogRepository vendorLogs) {
        this.reports = reports;
        this.properties = properties;
        this.storage = storage;
        this.otpRequests = otpRequests;
        this.vendorLogs = vendorLogs;
    }

    @Transactional
    public long deleteExpiredReports() {
        if (!properties.enabled() || properties.reportDays() <= 0) return 0;
        return deleteReports(reports.findByFetchedAtBefore(Instant.now().minus(properties.reportDays(), ChronoUnit.DAYS)));
    }

    @Transactional
    public long withdrawConsentAndDeleteCachedReports(String userId) {

        if (userId == null || userId.isBlank()) {
            return 0;
        }

        String normalizedUserId =
                userId.trim().toUpperCase();

        long count = deleteReports(reports.findByUserId(normalizedUserId));
        if (otpRequests != null) otpRequests.deleteAll(otpRequests.findByUserId(normalizedUserId));
        if (vendorLogs != null) vendorLogs.deleteByUserId(normalizedUserId);
        return count;
    }

    private long deleteReports(List<CreditScoreReportEntity> candidates) {
        long deleted = 0;
        for (CreditScoreReportEntity candidate : candidates) {
            try {
                storage.delete(candidate.getPdfS3ObjectKey());
            } catch (IOException e) {
                throw new IllegalStateException("Report PDF deletion failed; database record retained", e);
            }
            reports.delete(candidate);
            deleted++;
        }
        return deleted;
    }
}
