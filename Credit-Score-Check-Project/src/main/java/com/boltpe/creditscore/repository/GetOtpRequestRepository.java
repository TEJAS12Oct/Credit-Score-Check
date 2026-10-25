package com.boltpe.creditscore.repository;

import com.boltpe.creditscore.entity.GetOtpRequestEntity;
import com.boltpe.creditscore.enums.GetOtpStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GetOtpRequestRepository extends JpaRepository<GetOtpRequestEntity, Long> {

    List<GetOtpRequestEntity> findByUserId(String userId);

    Optional<GetOtpRequestEntity> findByRequestIdAndUserId(String requestId, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GetOtpRequestEntity r where r.requestId = :requestId and r.userId = :userId")
    Optional<GetOtpRequestEntity> findForVerification(@Param("requestId") String requestId, @Param("userId") String userId);

    Optional<GetOtpRequestEntity> findTopByUserIdAndOtpLockedUntilAfterOrderByOtpLockedUntilDesc(
            String userId, Instant now);

    Optional<GetOtpRequestEntity> findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(
            String userId,
            Collection<GetOtpStatus> statuses,
            Instant after);
}
