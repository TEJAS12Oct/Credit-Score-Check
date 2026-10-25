package com.boltpe.creditscore.service;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import java.util.Optional;
public interface CreditScoreReportService {
 void save(String requestId,String userId,String vendor,ApiResponse<SubmitOtpResponse> response);
 Optional<ApiResponse<SubmitOtpResponse>> findStoredResponse(String requestId,String userId);
}
