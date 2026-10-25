package com.boltpe.creditscore.service;

import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.config.CreditScorePullLimitProperties;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.impl.CreditScorePullLimitServiceImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditScorePullLimitServiceImplTest {
    private final CreditScoreReportRepository repository=mock(CreditScoreReportRepository.class);
    private final CreditScorePullLimitServiceImpl service =
            new CreditScorePullLimitServiceImpl(repository, new CreditScorePullLimitProperties(true, 720));
    private void existing(Duration age) {
        var e=new CreditScoreReportEntity();e.setFetchedAt(Instant.now().minus(age));
        when(repository.findTopByUserIdAndStatusOrderByFetchedAtDesc("u","SUCCESS"))
                .thenReturn(Optional.of(e));
    }
    @Test void firstTimeAllowed(){assertTrue(service.allowance("u").allowed());}
    @Test void refreshAfterTenDaysBlocked(){existing(Duration.ofDays(10));assertFalse(service.allowance("u").allowed());
        var ex=assertThrows(RequestValidationException.class,()->service.validateAllowed("u"));
        assertTrue(ex.getMessage().contains("refresh your credit score"));}
    @Test void refreshAfterTwentyNineDaysBlocked(){existing(Duration.ofDays(29));assertFalse(service.allowance("u").allowed());}
    @Test void refreshAfterThirtyOneDaysAllowed(){existing(Duration.ofDays(31));assertTrue(service.allowance("u").allowed());}
    @Test void disabledAllowsExistingScore(){existing(Duration.ofDays(1));var s=new CreditScorePullLimitServiceImpl(repository, new CreditScorePullLimitProperties(false, 720));assertTrue(s.allowance("u").allowed());}
    @Test void configuredHoursChangeAllowance(){existing(Duration.ofHours(25));var s=new CreditScorePullLimitServiceImpl(repository, new CreditScorePullLimitProperties(true, 24));assertTrue(s.allowance("u").allowed());}
}
