package com.boltpe.creditscore.service;

import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.GetOtpResponse;

public interface GetOtpService {
    ApiResponse<GetOtpResponse> generateOtp(String userId, String registeredMobile, GetOtpRequest request);
}
