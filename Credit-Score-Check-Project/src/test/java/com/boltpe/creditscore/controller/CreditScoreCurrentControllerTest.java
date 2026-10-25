package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.config.CreditScorePullLimitProperties;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditScoreCurrentControllerTest {
    private final CreditScoreReportRepository repository=mock(CreditScoreReportRepository.class);
    private final CreditScoreReportService reports=mock(CreditScoreReportService.class);
    private final CreditScoreCurrentController controller=new CreditScoreCurrentController(repository,reports,new CreditScorePullLimitProperties(true,720),mock(CreditScoreAnalyticsService.class));

    @Test void firstTimeUserHasNoPreviousScore() {
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->controller.existingScore("u")).getStatusCode());
    }
    @Test void existingScoreReturnsCountdownWithoutVendorCall() {
        var e = new CreditScoreReportEntity(); e.setRequestId("r");
        e.setFetchedAt(Instant.now().minus(Duration.ofDays(12)));
        when(repository.findTopByUserIdAndStatusOrderByFetchedAtDesc("u","SUCCESS"))
                .thenReturn(Optional.of(e));
        var data=new SubmitOtpResponse("r","SUCCESS",841,"EXCELLENT","Excellent",null,null,false,null,null);
        when(reports.findStoredResponse("r","u")).thenReturn(Optional.of(new ApiResponse<>(true,"OK","ok",data)));
        var response=controller.existingScore("u").getBody();
        assertNotNull(response);assertEquals(841,response.score().score());
        assertEquals(18,response.remainingDays());assertFalse(response.eligibleForRefresh());
        assertTrue(response.message().contains("18"));
    }
    @Test void expiredScoreShowsRefreshEnabled() {
        var e = new CreditScoreReportEntity();e.setRequestId("r");
        e.setFetchedAt(Instant.now().minus(Duration.ofDays(31)));
        when(repository.findTopByUserIdAndStatusOrderByFetchedAtDesc("u","SUCCESS")).thenReturn(Optional.of(e));
        when(reports.findStoredResponse("r","u")).thenReturn(Optional.of(new ApiResponse<>(true,"OK","ok",
                new SubmitOtpResponse("r","SUCCESS",800,"EXCELLENT","Good",null,null,false,null,null))));
        var response=controller.existingScore("u").getBody();
        assertNotNull(response);assertTrue(response.eligibleForRefresh()); assertEquals(0,response.remainingDays());
    }
}
