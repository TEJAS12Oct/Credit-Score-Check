package com.boltpe.creditscore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.boltpe.creditscore.client.kycflow.VendorClient;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.config.GetOtpProperties;
import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.GetOtpRequestEntity;
import com.boltpe.creditscore.enums.GetOtpStatus;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.exception.VendorApiException;
import com.boltpe.creditscore.mapper.SubmitOtpResponseMapper;
import com.boltpe.creditscore.repository.GetOtpRequestRepository;
import com.boltpe.creditscore.service.impl.SubmitOtpServiceImpl;
import com.boltpe.creditscore.validation.SubmitOtpRequestValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubmitOtpServiceImplTest {
    private GetOtpRequestRepository repository;
    private VendorClient vendorClient;
    private VendorApiLogService logService;
    private SubmitOtpServiceImpl service;
    private CreditScoreReportService reportService;
    private VendorErrorClassifier errorClassifier;
    private VendorResponseValidator responseValidator;
    private CreditScoreAnalyticsService analytics;
    private CreditScoreMetricsService metrics;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repository = mock(GetOtpRequestRepository.class);
        vendorClient = mock(VendorClient.class);
        logService = mock(VendorApiLogService.class);
        reportService = mock(CreditScoreReportService.class);
        errorClassifier = mock(VendorErrorClassifier.class);
        responseValidator = mock(VendorResponseValidator.class);
        analytics = mock(CreditScoreAnalyticsService.class);
        metrics = mock(CreditScoreMetricsService.class);
        when(errorClassifier.classify(anyInt(), any())).thenAnswer(inv -> {
            VendorSubmitOtpResponse r = inv.getArgument(1);
            if (r != null && r.message() != null && r.message().toLowerCase().contains("invalid otp")) return VendorErrorClassifier.Type.INVALID_OTP;
            return VendorErrorClassifier.Type.TECHNICAL;
        });
        service = new SubmitOtpServiceImpl(repository, vendorClient, logService,
                new SubmitOtpRequestValidator(), new SubmitOtpResponseMapper(),
                new GetOtpProperties(new GetOtpProperties.Otp(30, 3, 600, 3, 1800)), reportService, mock(com.boltpe.creditscore.service.CreditScorePullLimitService.class),
                errorClassifier, responseValidator, analytics, metrics);
    }

    @Test
    void verifiesOtpAndReturnsScore() throws Exception {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification("CSR-1", "user-1")).thenReturn(Optional.of(entity));
        var body = new VendorSubmitOtpResponse(200, "Data Found Successfully.",
                mapper.readTree("{\"SCORE\":{\"FCIREXScore\":774},\"CAIS_Account\":{\"CAIS_Account_DETAILS\":[{}]}}"));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(200, body));

        var response = service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456"));
        assertTrue(response.success());
        assertEquals(774, response.data().score());
        assertEquals(GetOtpStatus.OTP_VERIFIED, entity.getStatus());
        assertNotNull(entity.getOtpVerifiedAt());
        verify(reportService).save(eq("CSR-1"), eq("user-1"), eq("KYC_FLOW"), any());
        verify(logService).success(eq("CSR-1"), eq("user-1"), any(), any(), any(), any(), any(), eq(200), eq(200), any());
    }

    @Test
    void returnsNoHitForEmptySuccessfulResult() throws Exception {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(200,
                new VendorSubmitOtpResponse(200, "Data Found Successfully.", mapper.readTree("{}"))));
        var response = service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456"));
        assertEquals("NO_CREDIT_HISTORY", response.code());
        assertEquals(GetOtpStatus.NO_HIT, entity.getStatus());
    }

    @Test
    void incrementsInvalidOtpAttemptsAndLocksOnThirdAttempt() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setOtpVerificationAttempts(2);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(400,
                new VendorSubmitOtpResponse(400, "Invalid OTP", null)));
        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        RequestValidationException ex = assertThrows(RequestValidationException.class,
                () -> service.submitOtp("user-1", preparedRequest));
        assertEquals("OTP_ATTEMPTS_EXCEEDED", ex.getCode());
        assertEquals(GetOtpStatus.OTP_LOCKED, entity.getStatus());
        assertNotNull(entity.getOtpLockedUntil());
    }


    @Test
    void mapsVendor400InvalidDataWithoutConsumingOtpAttempt() {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(400,
                new VendorSubmitOtpResponse(400, "Invalid Data: Special characters", null)));
        when(errorClassifier.classify(anyInt(), any())).thenReturn(VendorErrorClassifier.Type.INVALID_REQUEST);

        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        VendorApiException ex = assertThrows(VendorApiException.class,
                () -> service.submitOtp("user-1", preparedRequest));

        assertEquals("INVALID_REQUEST_DATA", ex.getCode());
        assertEquals("Invalid request data. Please check the details and try again.", ex.getMessage());
        assertEquals(0, entity.getOtpVerificationAttempts());
    }

    @Test
    void mapsVendor402CreditsNotAvailable() {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(402,
                new VendorSubmitOtpResponse(402, "Credits Not Available", null)));
        when(errorClassifier.classify(anyInt(), any())).thenReturn(VendorErrorClassifier.Type.CREDITS_NOT_AVAILABLE);

        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        VendorApiException ex = assertThrows(VendorApiException.class,
                () -> service.submitOtp("user-1", preparedRequest));

        assertEquals("CREDIT_SCORE_SERVICE_UNAVAILABLE", ex.getCode());
        assertEquals("Credit score service is temporarily unavailable. Please try again later.", ex.getMessage());
    }

    @Test
    void mapsVendor404DataNotFoundWithoutTreatingItAsNoHit() {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(404,
                new VendorSubmitOtpResponse(404, "Data Not Found", null)));
        when(errorClassifier.classify(anyInt(), any())).thenReturn(VendorErrorClassifier.Type.DATA_NOT_FOUND);

        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        VendorApiException ex = assertThrows(VendorApiException.class,
                () -> service.submitOtp("user-1", preparedRequest));

        assertEquals("CREDIT_SCORE_DATA_NOT_FOUND", ex.getCode());
        assertEquals("Requested credit score data could not be found.", ex.getMessage());
        assertEquals(GetOtpStatus.OTP_SENT, entity.getStatus());
    }

    @Test
    void rejectsExpiredOtpBeforeVendorCall() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setRequestedAt(Instant.now().minusSeconds(601));
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_EXPIRED", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
        verifyNoInteractions(vendorClient);
    }

    @Test
    void resentOtpUsesLastSendTimeRatherThanOriginalRequestTime() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setRequestedAt(Instant.now().minusSeconds(800));
        entity.setLastOtpSentAt(Instant.now().minusSeconds(30));
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenThrow(new VendorApiException("VENDOR_UNAVAILABLE", "Temporary Issue"));
        SubmitOtpRequest request = new SubmitOtpRequest("CSR-1", "123456");
        assertThrows(VendorApiException.class, () -> service.submitOtp("user-1", request));
        verify(vendorClient).submitOtp(any());
    }

    @Test
    void resentOtpExpiresAfterLatestSend() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setRequestedAt(Instant.now().minusSeconds(900));
        entity.setLastOtpSentAt(Instant.now().minusSeconds(601));
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_EXPIRED", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
        verifyNoInteractions(vendorClient);
    }

    @Test
    void rejectsLockedOtpBeforeVendorCall() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setOtpLockedUntil(Instant.now().plusSeconds(60));
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_ATTEMPTS_EXCEEDED", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    @Test
    void rejectsUnknownSession() {
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.empty());
        assertCode("OTP_SESSION_NOT_FOUND", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    @Test
    void rejectsAlreadyVerifiedSession() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setStatus(GetOtpStatus.OTP_VERIFIED);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_ALREADY_VERIFIED", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    @Test
    void rejectsMissingVendorToken() {
        GetOtpRequestEntity entity = activeEntity();
        entity.setVendorToken(null);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_SESSION_INVALID", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    private GetOtpRequestEntity activeEntity() {
        return GetOtpRequestEntity.builder().requestId("CSR-1").userId("user-1")
                .status(GetOtpStatus.OTP_SENT).vendor("KYC_FLOW").vendorToken("secret-token")
                .otpSendCount(1).otpVerificationAttempts(0).requestedAt(Instant.now())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private void assertCode(String code, Runnable call) {
        RequestValidationException ex = assertThrows(RequestValidationException.class, call::run);
        assertEquals(code, ex.getCode());
    }


    @Test
    void replaysStoredResponseForAlreadyVerifiedSessionWithoutVendorCall() {
        GetOtpRequestEntity entity = activeEntity(); entity.setStatus(GetOtpStatus.OTP_VERIFIED);
        when(repository.findForVerification("CSR-1", "user-1")).thenReturn(Optional.of(entity));
        SubmitOtpResponse data = new SubmitOtpResponse("CSR-1", "SUCCESS", 774, "VERY_GOOD", "Strong", null, null, false, null, null);
        ApiResponse<SubmitOtpResponse> stored = ApiResponse.ok("CREDIT_SCORE_FETCHED", "ok", data);
        when(reportService.findStoredResponse("CSR-1", "user-1")).thenReturn(Optional.of(stored));
        var response = service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456"));
        assertSame(stored, response); verifyNoInteractions(vendorClient);
        verify(analytics).record("cs_otp_verified", "CSR-1", "user-1", "IDEMPOTENT_REPLAY");
    }

    @Test
    void rejectsAlreadyVerifiedWhenStoredResponseIsMissing() {
        GetOtpRequestEntity entity = activeEntity(); entity.setStatus(GetOtpStatus.OTP_VERIFIED);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(reportService.findStoredResponse("CSR-1", "user-1")).thenReturn(Optional.empty());
        assertCode("OTP_ALREADY_VERIFIED", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
        verifyNoInteractions(vendorClient);
    }

    @Test
    void rejectsSessionThatIsNotReady() {
        GetOtpRequestEntity entity = activeEntity(); entity.setStatus(GetOtpStatus.CREATED);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_NOT_READY", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    @Test
    void rejectsBlankVendorToken() {
        GetOtpRequestEntity entity = activeEntity(); entity.setVendorToken("   ");
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        assertCode("OTP_SESSION_INVALID", () -> service.submitOtp("user-1", new SubmitOtpRequest("CSR-1", "123456")));
    }

    @Test
    void invalidOtpBeforeLimitReturnsAttemptsLeftAndMarksVerifyFailed() {
        GetOtpRequestEntity entity = activeEntity(); entity.setOtpVerificationAttempts(0);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        var body = new VendorSubmitOtpResponse(400, "Invalid OTP", null);
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(400, body));
        when(errorClassifier.classify(anyInt(), any())).thenReturn(VendorErrorClassifier.Type.INVALID_OTP);
        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        RequestValidationException ex = assertThrows(RequestValidationException.class,
                () -> service.submitOtp("user-1", preparedRequest));
        assertEquals("INVALID_OTP", ex.getCode()); assertTrue(ex.getMessage().contains("2 attempts left"));
        assertEquals(1, entity.getOtpVerificationAttempts()); assertEquals(GetOtpStatus.OTP_VERIFY_FAILED, entity.getStatus());
    }

    @Test
    void mapsAuthRateLimitExpiredAndTechnicalFailures() {
        assertVendorFailure(VendorErrorClassifier.Type.AUTH, 401, "Forbidden", VendorApiException.class, "CREDIT_SCORE_SERVICE_UNAVAILABLE");
        assertVendorFailure(VendorErrorClassifier.Type.RATE_LIMIT, 429, "Too many requests", VendorApiException.class, "RATE_LIMITED");
        assertVendorFailure(VendorErrorClassifier.Type.EXPIRED_OTP, 400, "OTP expired", RequestValidationException.class, "OTP_EXPIRED");
        assertVendorFailure(VendorErrorClassifier.Type.TECHNICAL, 503, "Internal Server Error", VendorApiException.class, "VENDOR_ERROR");
    }

    @Test
    void propagatesVendorClientExceptionAndRecordsFailure() {
        GetOtpRequestEntity entity = activeEntity();
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        when(vendorClient.submitOtp(any())).thenThrow(new VendorApiException("CREDIT_SCORE_SERVICE_UNAVAILABLE", "Temporary Issue"));
        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        VendorApiException ex = assertThrows(VendorApiException.class,
                () -> service.submitOtp("user-1", preparedRequest));
        assertEquals("CREDIT_SCORE_SERVICE_UNAVAILABLE", ex.getCode());
        verify(analytics).record("cs_error", "CSR-1", "user-1", "CREDIT_SCORE_SERVICE_UNAVAILABLE");
    }

    private void assertVendorFailure(VendorErrorClassifier.Type type, int status, String message,
            Class<? extends RuntimeException> exceptionType, String expectedCode) {
        GetOtpRequestEntity entity = activeEntity();
        reset(repository, vendorClient, errorClassifier);
        when(repository.findForVerification(anyString(), anyString())).thenReturn(Optional.of(entity));
        VendorSubmitOtpResponse body = new VendorSubmitOtpResponse(status, message, null);
        when(vendorClient.submitOtp(any())).thenReturn(new VendorClient.VendorSubmitCallResult(status, body));
        when(errorClassifier.classify(anyInt(), any())).thenReturn(type);
        SubmitOtpRequest preparedRequest = new SubmitOtpRequest("CSR-1", "123456");
        RuntimeException ex = assertThrows(exceptionType,
                () -> service.submitOtp("user-1", preparedRequest));
        if (ex instanceof VendorApiException vendor) assertEquals(expectedCode, vendor.getCode());
        if (ex instanceof RequestValidationException validation) assertEquals(expectedCode, validation.getCode());
    }
}
