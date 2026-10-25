package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.service.CreditScoreDataLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-score")
public class CreditScoreDataController {

    private final CreditScoreDataLifecycleService lifecycleService;

    public CreditScoreDataController(CreditScoreDataLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @DeleteMapping("/data")
    public ResponseEntity<ApiResponse<Void>> deleteCreditScoreData(
            @RequestHeader("X-User-Id") String userId) {
        lifecycleService.withdrawConsentAndDeleteCachedReports(userId);
        return ResponseEntity.ok(ApiResponse.ok(
                "CREDIT_SCORE_DATA_DELETED",
                "Credit score data deleted successfully",
                null));
    }
}
