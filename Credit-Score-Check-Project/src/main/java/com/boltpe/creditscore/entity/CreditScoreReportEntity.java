package com.boltpe.creditscore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "credit_score_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_credit_score_report_request_id", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_credit_report_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_credit_report_score", columnList = "score")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 30)
    private String vendor;

    @Column(nullable = false, length = 30)
    private String status;

    private Integer score;

    @Column(name = "score_band", length = 30)
    private String scoreBand;

    @Column(length = 500)
    private String interpretation;

    @Column(name = "report_number", length = 150)
    private String reportNumber;

    @Column(name = "report_date", length = 30)
    private String reportDate;

    @Column(name = "report_time", length = 30)
    private String reportTime;

    @Column(name = "total_accounts")
    private Integer totalAccounts;

    @Column(name = "active_accounts")
    private Integer activeAccounts;

    @Column(name = "closed_accounts")
    private Integer closedAccounts;

    @Column(name = "total_outstanding")
    private Long totalOutstanding;

    @Column(name = "recent_enquiries")
    private Integer recentEnquiries;

    @Column(name = "partial_match", nullable = false)
    private boolean partialMatch;

    @Column(name = "no_hit", nullable = false)
    private boolean noHit;

    @Column(name = "caution_message", length = 500)
    private String cautionMessage;

    @Column(name = "boltpe_response_body", columnDefinition = "TEXT")
    private String boltpeResponseBody;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "pdf_s3_object_key", length = 600)
    private String pdfS3ObjectKey;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
