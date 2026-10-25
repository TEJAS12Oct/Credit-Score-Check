package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.GetOtpResponse;
import com.boltpe.creditscore.service.GetOtpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-score")
public class GetOtpController {

    private final GetOtpService creditScoreService;

    public GetOtpController(GetOtpService creditScoreService) {
        this.creditScoreService = creditScoreService;
    }

    @PostMapping("/getotp")
    public ResponseEntity<ApiResponse<GetOtpResponse>> generateOtp(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Registered-Mobile") String registeredMobile,
            @Valid @RequestBody GetOtpRequest request) {
        return ResponseEntity.ok(
                creditScoreService.generateOtp(userId, registeredMobile, request));
    }
}
