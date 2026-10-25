package com.boltpe.creditscore.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.service.SubmitOtpService;
import org.junit.jupiter.api.Test;

class SubmitOtpControllerTest {
    @Test
    void delegatesToService() {
        SubmitOtpService service = mock(SubmitOtpService.class);
        SubmitOtpResponse data = new SubmitOtpResponse("CSR-1", "SUCCESS", 774, "VERY_GOOD", "Strong profile", null, null, false, null, null);
        when(service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")))
                .thenReturn(ApiResponse.ok("CREDIT_SCORE_FETCHED", "Credit score fetched successfully", data));
        var response = new SubmitOtpController(service).submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456"));
        assertEquals(200, response.getStatusCode().value());
        assertEquals(774, response.getBody().data().score());
        verify(service).submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456"));
    }
}
