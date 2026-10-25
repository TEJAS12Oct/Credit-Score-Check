package com.boltpe.creditscore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.boltpe.creditscore.client.kycflow.VendorClient;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorGetOtpResponse;
import com.boltpe.creditscore.config.GetOtpProperties;
import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.entity.GetOtpRequestEntity;
import com.boltpe.creditscore.enums.GetOtpStatus;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.exception.VendorApiException;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import com.boltpe.creditscore.repository.GetOtpRequestRepository;
import com.boltpe.creditscore.service.impl.GetOtpServiceImpl;
import com.boltpe.creditscore.validation.GetOtpRequestValidator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GetOtpServiceImplTest {

    private static final String USER_ID = "u1";
    private static final String MOBILE = "9876543210";
    private static final DateTimeFormatter DOB_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final GetOtpRequestRepository repository =
            mock(GetOtpRequestRepository.class);
    private final ConsentService consentService = mock(ConsentService.class);
    private final VendorApiLogService apiLogService = mock(VendorApiLogService.class);
    private final VendorClient kycFlowClient = mock(VendorClient.class);
    private final CreditScoreAnalyticsService analytics = mock(CreditScoreAnalyticsService.class);
    private final GetOtpProperties properties =
            new GetOtpProperties(new GetOtpProperties.Otp(30, 3, 600, 3, 1800));

    private final GetOtpServiceImpl service = new GetOtpServiceImpl(
            repository,
            consentService,
            apiLogService,
            new GetOtpVendorMapper(),
            kycFlowClient,
            properties,
            new GetOtpRequestValidator(), mock(com.boltpe.creditscore.service.CreditScorePullLimitService.class), analytics);

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsOtpCreatesRequestStoresTokenAndConsent() {
        when(kycFlowClient.generateOtp(any())).thenReturn(successfulVendorCall());

        var response = service.generateOtp(USER_ID, MOBILE, validRequest());

        assertTrue(response.success());
        assertEquals("OTP_SENT", response.code());
        assertEquals(2, response.data().remainingResends());

        ArgumentCaptor<GetOtpRequestEntity> captor =
                ArgumentCaptor.forClass(GetOtpRequestEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        GetOtpRequestEntity saved = captor.getAllValues().getLast();
        assertEquals(1, saved.getOtpSendCount());
        assertEquals("vendor-request-1", saved.getVendorRequestId());
        assertEquals("vendor-token-1", saved.getVendorToken());
        assertEquals(GetOtpStatus.OTP_SENT, saved.getStatus());
        assertNotNull(saved.getLastOtpSentAt());

        verify(consentService).record(anyString(), eq(USER_ID));
        verify(apiLogService).success(
                anyString(), eq(USER_ID), any(), any(), any(), any(), any(),
                eq(200), eq(200), eq("ok"));
    }

    @Test
    void resendUpdatesSameRequestAndIncrementsOtpCount() {
        GetOtpRequestEntity existing = activeRequest(1, Instant.now().minusSeconds(31));
        when(repository.findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(
                eq(USER_ID), any(), any()))
                .thenReturn(Optional.of(existing));
        when(kycFlowClient.generateOtp(any())).thenReturn(successfulVendorCall());

        var response = service.generateOtp(USER_ID, MOBILE, validRequest());

        assertEquals(2, existing.getOtpSendCount());
        assertEquals(1, response.data().remainingResends());
        verify(consentService, never()).record(anyString(), anyString());
    }

    @Test
    void blocksResendDuringCooldown() {
        GetOtpRequestEntity existing = activeRequest(1, Instant.now());
        when(repository.findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(
                eq(USER_ID), any(), any()))
                .thenReturn(Optional.of(existing));

        GetOtpRequest preparedRequest = validRequest();
        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("OTP_RESEND_COOLDOWN", exception.getCode());
        verifyNoInteractions(kycFlowClient);
    }

    @Test
    void blocksFourthOtpSend() {
        GetOtpRequest preparedRequest = validRequest();
        GetOtpRequestEntity existing = activeRequest(3, Instant.now().minusSeconds(60));
        when(repository.findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(
                eq(USER_ID), any(), any()))
                .thenReturn(Optional.of(existing));

        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("OTP_SEND_LIMIT_EXCEEDED", exception.getCode());
        verifyNoInteractions(kycFlowClient);
    }

    @Test
    void blocksMobileMismatch() {
        GetOtpRequest preparedRequest = validRequest();
        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, "9999999999", preparedRequest));

        assertEquals("MOBILE_MISMATCH", exception.getCode());
        verifyNoInteractions(kycFlowClient);
    }

    @Test
    void blocksMissingAuthenticatedUser() {
        GetOtpRequest preparedRequest = validRequest();
        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(" ", MOBILE, preparedRequest));

        assertEquals("AUTHENTICATION_REQUIRED", exception.getCode());
    }

    @Test
    void blocksMissingRegisteredMobile() {
        GetOtpRequest preparedRequest = validRequest();
        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, " ", preparedRequest));

        assertEquals("REGISTERED_MOBILE_REQUIRED", exception.getCode());
    }

    @Test
    void blocksInvalidConsent() {
        GetOtpRequest request = requestWith(
                LocalDate.now().minusYears(25).format(DOB_FORMATTER),
                "wrong consent");

        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, request));

        assertEquals("INVALID_CONSENT", exception.getCode());
        verifyNoInteractions(kycFlowClient);
    }

    @Test
    void blocksMinor() {
        GetOtpRequest request = requestWith(
                LocalDate.now().minusYears(17).format(DOB_FORMATTER),
                GetOtpVendorMapper.CONSENT_TEXT);

        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, request));

        assertEquals("AGE_NOT_ELIGIBLE", exception.getCode());
    }

    @Test
    void blocksInvalidCalendarDate() {
        GetOtpRequest request = requestWith(
                "31-02-1995",
                GetOtpVendorMapper.CONSENT_TEXT);

        RequestValidationException exception = assertThrows(
                RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, request));

        assertEquals("INVALID_DOB", exception.getCode());
    }

    @Test
    void mapsVendor400AndWritesFailureLog() {
        when(kycFlowClient.generateOtp(any())).thenReturn(
                new VendorClient.VendorCallResult(
                        400,
                        new VendorGetOtpResponse(400, "bad", null)));

        GetOtpRequest preparedRequest = validRequest();

        VendorApiException exception = assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("INVALID_REQUEST_DATA", exception.getCode());
        assertEquals("Invalid request data. Please check the details and try again.", exception.getMessage());
        verify(apiLogService).failure(
                anyString(), eq(USER_ID), any(), any(), any(), any(),
                eq(400), eq(400), eq("bad"), eq("INVALID_REQUEST_DATA"), anyString());
    }

    @Test
    void mapsVendor402CreditsNotAvailable() {
        when(kycFlowClient.generateOtp(any())).thenReturn(
                new VendorClient.VendorCallResult(
                        402,
                        new VendorGetOtpResponse(402, "Credits Not Available", null)));

        GetOtpRequest preparedRequest = validRequest();

        VendorApiException exception = assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("CREDIT_SCORE_SERVICE_UNAVAILABLE", exception.getCode());
        assertEquals("Credit score service is temporarily unavailable. Please try again later.", exception.getMessage());
    }

    @Test
    void mapsVendor404() {
        when(kycFlowClient.generateOtp(any())).thenReturn(
                new VendorClient.VendorCallResult(
                        404,
                        new VendorGetOtpResponse(404, "not found", null)));

        GetOtpRequest preparedRequest = validRequest();

        VendorApiException exception = assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("CREDIT_SCORE_DATA_NOT_FOUND", exception.getCode());
        assertEquals("Requested credit score data could not be found.", exception.getMessage());
    }

    @Test
    void mapsVendor429() {
        when(kycFlowClient.generateOtp(any())).thenReturn(
                new VendorClient.VendorCallResult(
                        429,
                        new VendorGetOtpResponse(429, "busy", null)));

        GetOtpRequest preparedRequest = validRequest();

        VendorApiException exception = assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("VENDOR_RATE_LIMITED", exception.getCode());
    }

    @Test
    void mapsNullVendorBody() {
        when(kycFlowClient.generateOtp(any()))
                .thenReturn(new VendorClient.VendorCallResult(502, null));

        GetOtpRequest preparedRequest = validRequest();

        VendorApiException exception = assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        assertEquals("VENDOR_INVALID_RESPONSE", exception.getCode());
    }

    @Test
    void mapsVendorTransportFailureAndWritesFailureLog() {
        GetOtpRequest preparedRequest = validRequest();
        when(kycFlowClient.generateOtp(any())).thenThrow(
                new VendorApiException("CREDIT_SCORE_SERVICE_UNAVAILABLE", "down"));

        assertThrows(
                VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));

        verify(apiLogService).failure(
                anyString(), eq(USER_ID), any(), any(), any(), isNull(),
                isNull(), isNull(), isNull(),
                eq("CREDIT_SCORE_SERVICE_UNAVAILABLE"), eq("down"));
    }


    @Test
    void blocksUserWithActiveOtpLock() {
        GetOtpRequestEntity locked = activeRequest(1, Instant.now().minusSeconds(60));
        locked.setOtpLockedUntil(Instant.now().plusSeconds(300));
        when(repository.findTopByUserIdAndOtpLockedUntilAfterOrderByOtpLockedUntilDesc(eq(USER_ID), any()))
                .thenReturn(Optional.of(locked));
        GetOtpRequest preparedRequest = validRequest();
        RequestValidationException ex = assertThrows(RequestValidationException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));
        assertEquals("OTP_ATTEMPTS_EXCEEDED", ex.getCode());
        verifyNoInteractions(kycFlowClient);
    }

    @Test
    void existingRequestWithoutLastSentTimeCanSendOtp() {
        GetOtpRequestEntity existing = activeRequest(0, null);
        when(repository.findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(eq(USER_ID), any(), any()))
                .thenReturn(Optional.of(existing));
        when(kycFlowClient.generateOtp(any())).thenReturn(successfulVendorCall());
        var response = service.generateOtp(USER_ID, MOBILE, validRequest());
        assertEquals("OTP_SENT", response.code());
        assertEquals(1, existing.getOtpSendCount());
    }

    @Test
    void mapsUnknownVendorFailureToServiceUnavailable() {
        when(kycFlowClient.generateOtp(any())).thenReturn(
                new VendorClient.VendorCallResult(503, new VendorGetOtpResponse(503, "Internal Server Error", null)));
        GetOtpRequest preparedRequest = validRequest();
        VendorApiException ex = assertThrows(VendorApiException.class,
                () -> service.generateOtp(USER_ID, MOBILE, preparedRequest));
        assertEquals("CREDIT_SCORE_SERVICE_UNAVAILABLE", ex.getCode());
        assertEquals("Couldn’t send OTP. Please try again later.", ex.getMessage());
    }

    private GetOtpRequest validRequest() {
        return requestWith(
                LocalDate.now().minusYears(25).format(DOB_FORMATTER),
                GetOtpVendorMapper.CONSENT_TEXT);
    }

    private GetOtpRequest requestWith(String dob, String consent) {
        return new GetOtpRequest(
                "Tejas",
                "Jawale",
                "ABCDE1234F",
                dob,
                MOBILE,
                "a@b.com",
                "male",
                "411001",
                consent);
    }

    private GetOtpRequestEntity activeRequest(int otpSendCount, Instant lastOtpSentAt) {
        Instant now = Instant.now();
        return GetOtpRequestEntity.builder()
                .requestId("CS-EXISTING")
                .userId(USER_ID)
                .status(GetOtpStatus.OTP_SENT)
                .vendor("KYC_FLOW")
                .otpSendCount(otpSendCount)
                .lastOtpSentAt(lastOtpSentAt)
                .requestedAt(now.minusSeconds(120))
                .createdAt(now.minusSeconds(120))
                .updatedAt(now.minusSeconds(120))
                .build();
    }

    private VendorClient.VendorCallResult successfulVendorCall() {
        VendorGetOtpResponse.SendOtpResponse sendOtpResponse =
                new VendorGetOtpResponse.SendOtpResponse(
                        true,
                        "vendor-request-1",
                        List.of("ok"));
        VendorGetOtpResponse response = new VendorGetOtpResponse(
                200,
                "ok",
                new VendorGetOtpResponse.Result(sendOtpResponse, "vendor-token-1"));
        return new VendorClient.VendorCallResult(200, response);
    }
}
