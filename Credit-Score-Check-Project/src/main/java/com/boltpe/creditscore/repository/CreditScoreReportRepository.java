package com.boltpe.creditscore.repository;

import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface CreditScoreReportRepository extends JpaRepository<CreditScoreReportEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CreditScoreReportEntity r where r.requestId = :requestId and r.userId = :userId")
    Optional<CreditScoreReportEntity> findForPdfUpdate(@Param("requestId") String requestId, @Param("userId") String userId);

    List<CreditScoreReportEntity> findByFetchedAtBefore(Instant before);
    List<CreditScoreReportEntity> findByUserId(String userId);

    Optional<CreditScoreReportEntity> findByRequestId(String requestId);
    Optional<CreditScoreReportEntity> findByRequestIdAndUserId(String requestId, String userId);
    Optional<CreditScoreReportEntity> findTopByUserIdAndFetchedAtAfterOrderByFetchedAtDesc(String userId, Instant after);
    Optional<CreditScoreReportEntity> findTopByUserIdAndStatusOrderByFetchedAtDesc(String userId, String status);
    long deleteByFetchedAtBefore(Instant before);
    long deleteByUserId(String userId);
}
