package com.boltpe.creditscore.service;
import java.time.Instant;
public interface CreditScorePullLimitService {
 void validateAllowed(String userId);
 record Allowance(boolean allowed, Instant nextEligibleAt, long remainingSeconds) {}
 Allowance allowance(String userId);
}
