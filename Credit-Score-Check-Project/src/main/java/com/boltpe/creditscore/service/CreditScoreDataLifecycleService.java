package com.boltpe.creditscore.service;
public interface CreditScoreDataLifecycleService {
 long deleteExpiredReports();
 long withdrawConsentAndDeleteCachedReports(String userId);
}
