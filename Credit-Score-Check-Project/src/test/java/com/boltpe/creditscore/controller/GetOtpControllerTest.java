package com.boltpe.creditscore.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.GetOtpResponse;
import com.boltpe.creditscore.exception.GlobalExceptionHandler;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.service.GetOtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class GetOtpControllerTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        GetOtpService service = mock(GetOtpService.class);
        when(service.generateOtp(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    var request = (com.boltpe.creditscore.dto.request.GetOtpRequest) invocation.getArgument(2);
                    if ("INVALID".equals(request.panNumber())) {
                        throw new RequestValidationException("INVALID_PAN", "Invalid PAN number format");
                    }
                    return ApiResponse.ok(
                            "OTP_SENT",
                            "OTP sent successfully",
                            new GetOtpResponse("CS-1", true, 30, 2));
                });

        client = WebTestClient.bindToController(new GetOtpController(service))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsRequiredBoltPeRequestContract() {
        client.post()
                .uri("/api/v1/credit-score/getotp")
                .header("X-User-Id", "u1")
                .header("X-Registered-Mobile", "9876543210")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo("OTP_SENT")
                .jsonPath("$.data.requestId").isEqualTo("CS-1")
                .jsonPath("$.data.remainingResends").isEqualTo(2);
    }

    @Test
    void rejectsInvalidPan() {
        client.post()
                .uri("/api/v1/credit-score/getotp")
                .header("X-User-Id", "u1")
                .header("X-Registered-Mobile", "9876543210")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody().replace("ABCDE1234F", "INVALID"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PAN");
    }

    @Test
    void rejectsMalformedJson() {
        client.post()
                .uri("/api/v1/credit-score/getotp")
                .header("X-User-Id", "u1")
                .header("X-Registered-Mobile", "9876543210")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{invalid-json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsMissingRequiredHeader() {
        client.post()
                .uri("/api/v1/credit-score/getotp")
                .header("X-User-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validBody())
                .exchange()
                .expectStatus().isBadRequest();
    }

    private String validBody() {
        return """
                {
                  "firstName": "Tejas",
                  "lastname": "Jawale",
                  "panNumber": "ABCDE1234F",
                  "dob": "09-05-1995",
                  "mobileNumber": "9876543210",
                  "email": "a@b.com",
                  "gender": "male",
                  "pinCode": "411001",
                  "consent": "I explicitly consent to the collection, processing, and verification of my data for authentication, KYC, and compliance purposes."
                }
                """;
    }
}
