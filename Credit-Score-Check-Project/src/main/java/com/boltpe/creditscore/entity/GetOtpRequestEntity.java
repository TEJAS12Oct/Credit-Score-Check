package com.boltpe.creditscore.entity;

import com.boltpe.creditscore.enums.GetOtpStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "credit_score_get_otp_request",
        indexes = @Index(name = "idx_csr_user_created", columnList = "user_id,created_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetOtpRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "request_id", nullable = false, unique = true, length = 50)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GetOtpStatus status;

    @Column(nullable = false, length = 30)
    private String vendor;

    @Column(name = "vendor_request_id", length = 150)
    private String vendorRequestId;

    @Column(name = "vendor_token", length = 2048)
    private String vendorToken;

    @Column(name = "otp_send_count", nullable = false)
    private int otpSendCount;

    @Column(name = "last_otp_sent_at")
    private Instant lastOtpSentAt;

    @Column(name = "otp_verification_attempts", nullable = false)
    @Builder.Default
    private int otpVerificationAttempts = 0;

    @Column(name = "otp_locked_until")
    private Instant otpLockedUntil;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
