package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.util.SensitiveDataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-score/debug")
@Profile("local")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "credit-score.debug", name = "enabled", havingValue = "true")
public class CreditScoreDebugController {

    private final CreditScoreReportRepository reportRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public CreditScoreDebugController(
            CreditScoreReportRepository reportRepository,
            SensitiveDataEncryptionService encryptionService,
            ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/decrypt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decryptReport(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DecryptReportRequest request) {

        CreditScoreReportEntity report = reportRepository
                .findByRequestIdAndUserId(request.requestId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Credit score report not found"));

        try {
            String decrypted = encryptionService.decrypt(report.getBoltpeResponseBody());
            JsonNode decryptedData = objectMapper.readTree(decrypted);
            Map<String, Object> data = Map.of(
                    "requestId", request.requestId(),
                    "decryptedData", decryptedData);
            return ResponseEntity.ok(ApiResponse.ok(
                    "CREDIT_SCORE_DATA_DECRYPTED",
                    "Credit score data decrypted successfully",
                    data));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt stored credit-score report", ex);
        }
    }

    public record DecryptReportRequest(@NotBlank String requestId) {}
}
