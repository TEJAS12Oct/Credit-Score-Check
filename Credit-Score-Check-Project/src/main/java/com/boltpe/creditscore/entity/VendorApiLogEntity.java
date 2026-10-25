package com.boltpe.creditscore.entity;

import com.boltpe.creditscore.enums.VendorApiOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "credit_score_vendor_api_log", indexes = {
        @Index(name = "idx_vendor_log_request_operation", columnList = "request_id,operation"),
        @Index(name = "idx_vendor_log_user_created", columnList = "user_id,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorApiLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorApiOperation operation;

    @Column(name = "boltpe_request_body", columnDefinition = "TEXT")
    private String boltpeRequestBody;

    @Column(name = "boltpe_response_body", columnDefinition = "TEXT")
    private String boltpeResponseBody;

    @Column(name = "vendor_request_body", columnDefinition = "TEXT")
    private String vendorRequestBody;

    @Column(name = "vendor_response_body", columnDefinition = "TEXT")
    private String vendorResponseBody;

    @Column(name = "vendor_http_status")
    private Integer vendorHttpStatus;

    @Column(name = "vendor_code")
    private Integer vendorCode;

    @Column(name = "vendor_message", length = 500)
    private String vendorMessage;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
