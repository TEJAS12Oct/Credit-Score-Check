package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import java.time.Duration;
import java.time.Instant;
import com.boltpe.creditscore.config.CreditScorePullLimitProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/credit-score")
public class CreditScoreCurrentController {
    private final CreditScoreReportRepository repository;
    private final CreditScoreReportService reports;
    private final CreditScorePullLimitProperties pullLimit;
    private final CreditScoreAnalyticsService analytics;

    public CreditScoreCurrentController(CreditScoreReportRepository repository,
                                         CreditScoreReportService reports, CreditScorePullLimitProperties pullLimit,
                                         CreditScoreAnalyticsService analytics) {
        this.repository = repository;
        this.reports = reports;
        this.pullLimit = pullLimit;
        this.analytics = analytics;
    }

    @GetMapping("/score")
    public ResponseEntity<ExistingScoreResponse> existingScore(@RequestHeader("X-User-Id") String userId) {
        CreditScoreReportEntity entity = repository
                .findTopByUserIdAndStatusOrderByFetchedAtDesc(userId, "SUCCESS")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No credit score found"));
        Instant next = entity.getFetchedAt().plus(Duration.ofHours(pullLimit.hours()));
        long seconds = pullLimit.enabled() ? Math.max(0, Duration.between(Instant.now(), next).getSeconds()) : 0;
        long days = (seconds + 86399) / 86400;
        ApiResponse<SubmitOtpResponse> report = reports.findStoredResponse(entity.getRequestId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        analytics.record("cs_score_viewed", entity.getRequestId(), userId, "EXISTING_SCORE");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ExistingScoreResponse(
                report.data(), entity.getFetchedAt(), next, days, seconds, seconds == 0,
                seconds == 0 ? "You can refresh your credit score now."
                        : "You can refresh your credit score after " + days + " day(s)."));
    }

    public record ExistingScoreResponse(SubmitOtpResponse score,
            Instant scoreGeneratedAt, Instant nextEligibleAt, long remainingDays,
            long remainingSeconds, boolean eligibleForRefresh, String message) {}
}
