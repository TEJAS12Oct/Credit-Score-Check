package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.config.CreditScorePullLimitProperties;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScorePullLimitService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CreditScorePullLimitServiceImpl implements CreditScorePullLimitService {
    private final CreditScorePullLimitProperties properties;
    private final CreditScoreReportRepository repository;

    public CreditScorePullLimitServiceImpl(CreditScoreReportRepository repository, CreditScorePullLimitProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void validateAllowed(String userId) {
        Allowance a = allowance(userId);
        if (!a.allowed()) {
            long days = (a.remainingSeconds() + 86399) / 86400;
            throw new RequestValidationException("CREDIT_SCORE_REFRESH_NOT_ALLOWED",
                    "You can refresh your credit score after " + days + " day(s). Next eligible at "
                            + a.nextEligibleAt());
        }
    }

    @Override
    public Allowance allowance(String userId) {
        if (!properties.enabled()) return new Allowance(true, null, 0);
        Instant now = Instant.now();
        return repository.findTopByUserIdAndStatusOrderByFetchedAtDesc(userId, "SUCCESS")
                .map(report -> {
                    Instant next = report.getFetchedAt().plus(Duration.ofHours(properties.hours()));
                    long seconds = Math.max(0, Duration.between(now, next).getSeconds());
                    return seconds == 0 ? new Allowance(true, next, 0)
                            : new Allowance(false, next, seconds);
                })
                .orElseGet(() -> new Allowance(true, null, 0));
    }
}
