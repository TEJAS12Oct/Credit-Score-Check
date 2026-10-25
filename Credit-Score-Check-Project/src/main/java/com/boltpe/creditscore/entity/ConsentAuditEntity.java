package com.boltpe.creditscore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "credit_score_consent_audit", indexes = {
        @Index(name = "idx_consent_request", columnList = "request_id"),
        @Index(name = "idx_consent_user_created", columnList = "user_id,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    @Column(name = "consent_text", nullable = false, length = 1000)
    private String consentText;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
