package com.boltpe.creditscore.service;

import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;

public interface SubmitOtpService {
    ApiResponse<SubmitOtpResponse> submitOtp(String userId, SubmitOtpRequest request);
}
