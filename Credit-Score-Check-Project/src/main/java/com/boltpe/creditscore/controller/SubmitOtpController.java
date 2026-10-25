package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.service.SubmitOtpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credit-score")
public class SubmitOtpController {
    private final SubmitOtpService service;

    public SubmitOtpController(SubmitOtpService service) {
        this.service = service;
    }

    @PostMapping("/submit-otp")
    public ResponseEntity<ApiResponse<SubmitOtpResponse>> submitOtp(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SubmitOtpRequest request) {
        return ResponseEntity.ok(service.submitOtp(userId, request));
    }
}
