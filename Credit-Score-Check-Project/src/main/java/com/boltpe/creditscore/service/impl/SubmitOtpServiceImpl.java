package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.client.kycflow.VendorClient;
import com.boltpe.creditscore.client.kycflow.dto.request.VendorSubmitOtpRequest;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.config.GetOtpProperties;
import com.boltpe.creditscore.dto.request.SubmitOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.GetOtpRequestEntity;
import com.boltpe.creditscore.enums.GetOtpStatus;
import com.boltpe.creditscore.enums.VendorApiOperation;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.exception.VendorApiException;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import com.boltpe.creditscore.mapper.SubmitOtpResponseMapper;
import com.boltpe.creditscore.repository.GetOtpRequestRepository;
import com.boltpe.creditscore.service.SubmitOtpService;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.service.CreditScorePullLimitService;
import com.boltpe.creditscore.service.VendorApiLogService;
import com.boltpe.creditscore.service.VendorErrorClassifier;
import com.boltpe.creditscore.service.VendorResponseValidator;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import com.boltpe.creditscore.service.CreditScoreMetricsService;
import com.boltpe.creditscore.validation.SubmitOtpRequestValidator;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SubmitOtpServiceImpl implements SubmitOtpService {
    private final GetOtpRequestRepository repository;
    private final VendorClient vendorClient;
    private final VendorApiLogService apiLogService;
    private final SubmitOtpRequestValidator validator;
    private final SubmitOtpResponseMapper responseMapper;
    private final GetOtpProperties properties;
    private final CreditScoreReportService reportService;
    private final CreditScorePullLimitService pullLimitService;
    private final VendorErrorClassifier errorClassifier;
    private final VendorResponseValidator responseValidator;
    private final CreditScoreAnalyticsService analytics;
    private final CreditScoreMetricsService metrics;

    public SubmitOtpServiceImpl(GetOtpRequestRepository repository, VendorClient vendorClient,
            VendorApiLogService apiLogService, SubmitOtpRequestValidator validator,
            SubmitOtpResponseMapper responseMapper, GetOtpProperties properties,
            CreditScoreReportService reportService, CreditScorePullLimitService pullLimitService,
            VendorErrorClassifier errorClassifier, VendorResponseValidator responseValidator,
            CreditScoreAnalyticsService analytics, CreditScoreMetricsService metrics) {
        this.repository = repository;
        this.vendorClient = vendorClient;
        this.apiLogService = apiLogService;
        this.validator = validator;
        this.responseMapper = responseMapper;
        this.properties = properties;
        this.reportService = reportService;
        this.pullLimitService = pullLimitService;
        this.errorClassifier = errorClassifier;
        this.responseValidator = responseValidator;
        this.analytics = analytics;
        this.metrics = metrics;
    }

    @Override
    @Transactional(noRollbackFor = RequestValidationException.class)
    public ApiResponse<SubmitOtpResponse> submitOtp(String userId, SubmitOtpRequest request) {
        long started = System.nanoTime(); boolean success = false;
        try {
            validator.validate(userId, request);
            Instant now = Instant.now();
            GetOtpRequestEntity entity = repository.findForVerification(request.requestId(), userId)
                    .orElseThrow(() -> new RequestValidationException("OTP_SESSION_NOT_FOUND", "OTP session not found"));

            // Semantic idempotency: retrying a successfully completed request returns the stored response
            // without another vendor call or another billable bureau pull.
            if (entity.getStatus() == GetOtpStatus.OTP_VERIFIED || entity.getStatus() == GetOtpStatus.NO_HIT) {
                ApiResponse<SubmitOtpResponse> replay = reportService.findStoredResponse(entity.getRequestId(), userId)
                        .orElseThrow(() -> new RequestValidationException("OTP_ALREADY_VERIFIED", "OTP has already been verified"));
                success = true; analytics.record("cs_otp_verified", entity.getRequestId(), userId, "IDEMPOTENT_REPLAY");
                return replay;
            }

            validateSession(entity, now);
            pullLimitService.validateAllowed(userId);
            VendorSubmitOtpRequest vendorRequest = new VendorSubmitOtpRequest(entity.getVendorToken(), request.otp(), GetOtpVendorMapper.CONSENT_TEXT);
            VendorClient.VendorSubmitCallResult vendorCall;
            try { vendorCall = vendorClient.submitOtp(vendorRequest); }
            catch (VendorApiException ex) {
                apiLogService.failure(entity.getRequestId(), userId, VendorApiOperation.SUBMIT_OTP, request, vendorRequest, null, null, null, null, ex.getCode(), ex.getMessage());
                analytics.record("cs_error", entity.getRequestId(), userId, ex.getCode()); throw ex;
            }

            VendorSubmitOtpResponse vendorResponse = vendorCall.body();
            if (vendorResponse == null || !vendorResponse.isSuccess()) {
                VendorErrorClassifier.Type type = errorClassifier.classify(vendorCall.httpStatus(), vendorResponse);
                log.warn("Credit-score vendor submit OTP failed: requestId={}, httpStatus={}, vendorCode={}, vendorMessage={}, classification={}",
                        entity.getRequestId(), vendorCall.httpStatus(),
                        vendorResponse == null ? null : vendorResponse.code(),
                        safeVendorMessage(vendorResponse == null ? null : vendorResponse.message()), type);
                switch (type) {
                    case AUTH -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"CREDIT_SCORE_SERVICE_UNAVAILABLE","Temporary Issue"); throw new VendorApiException("CREDIT_SCORE_SERVICE_UNAVAILABLE","Temporary Issue"); }
                    case RATE_LIMIT -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"RATE_LIMITED","You've reached the limit. Please try again later"); throw new VendorApiException("RATE_LIMITED","You've reached the limit. Please try again later"); }
                    case EXPIRED_OTP -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"OTP_EXPIRED","Code Expired. Please request a new OTP"); analytics.record("cs_otp_failed",entity.getRequestId(),userId,"OTP_EXPIRED"); throw new RequestValidationException("OTP_EXPIRED","Code Expired. Please request a new OTP"); }
                    case INVALID_OTP -> handleVerificationFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,now);
                    case INVALID_REQUEST -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"INVALID_REQUEST_DATA","Invalid request data. Please check the details and try again."); throw new VendorApiException("INVALID_REQUEST_DATA","Invalid request data. Please check the details and try again."); }
                    case CREDITS_NOT_AVAILABLE -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"CREDIT_SCORE_SERVICE_UNAVAILABLE","Credit score service is temporarily unavailable. Please try again later."); throw new VendorApiException("CREDIT_SCORE_SERVICE_UNAVAILABLE","Credit score service is temporarily unavailable. Please try again later."); }
                    case DATA_NOT_FOUND -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"CREDIT_SCORE_DATA_NOT_FOUND","Requested credit score data could not be found."); throw new VendorApiException("CREDIT_SCORE_DATA_NOT_FOUND","Requested credit score data could not be found."); }
                    case TECHNICAL -> { logVendorFailure(entity,userId,request,vendorRequest,vendorCall,vendorResponse,"VENDOR_ERROR","Service Busy. Please try again later"); analytics.record("cs_error",entity.getRequestId(),userId,"VENDOR_ERROR"); throw new VendorApiException("VENDOR_ERROR","Service Busy. Please try again later"); }
                }
            }
            responseValidator.validateSuccessful(vendorResponse);
            SubmitOtpResponse responseData = responseMapper.toResponse(entity.getRequestId(), vendorResponse.result());
            entity.setStatus("NO_HIT".equals(responseData.status()) ? GetOtpStatus.NO_HIT : GetOtpStatus.OTP_VERIFIED);
            entity.setOtpVerifiedAt(now); entity.setUpdatedAt(now); repository.save(entity);
            ApiResponse<SubmitOtpResponse> response = ApiResponse.ok("NO_HIT".equals(responseData.status()) ? "NO_CREDIT_HISTORY" : "CREDIT_SCORE_FETCHED", "NO_HIT".equals(responseData.status()) ? "No Credit History Found" : "Credit score fetched successfully", responseData);
            reportService.save(entity.getRequestId(), userId, "KYC_FLOW", response);
            apiLogService.success(entity.getRequestId(),userId,VendorApiOperation.SUBMIT_OTP,request,response,vendorRequest,vendorResponse,vendorCall.httpStatus(),vendorResponse.code(),vendorResponse.message());
            analytics.record("NO_HIT".equals(responseData.status()) ? "cs_no_hit" : "cs_otp_verified", entity.getRequestId(), userId, responseData.status());
            success = true; return response;
        } finally { metrics.recordSubmitOtpDuration((System.nanoTime()-started)/1_000_000L, success); }
    }

    private void validateSession(GetOtpRequestEntity entity, Instant now) {
        if (entity.getOtpLockedUntil() != null && entity.getOtpLockedUntil().isAfter(now)) {
            throw new RequestValidationException("OTP_ATTEMPTS_EXCEEDED", "Too many tries. Please try again later");
        }
        if ((entity.getLastOtpSentAt() == null ? entity.getRequestedAt() : entity.getLastOtpSentAt())
                .plusSeconds(properties.otp().sessionValiditySeconds()).isBefore(now)) {
            throw new RequestValidationException("OTP_EXPIRED", "Code Expired. Please request a new OTP");
        }
        if (entity.getStatus() != GetOtpStatus.OTP_SENT && entity.getStatus() != GetOtpStatus.OTP_VERIFY_FAILED
                && entity.getStatus() != GetOtpStatus.OTP_LOCKED) {
            throw new RequestValidationException("OTP_NOT_READY", "OTP is not available for verification");
        }
        if (entity.getVendorToken() == null || entity.getVendorToken().isBlank()) {
            throw new RequestValidationException("OTP_SESSION_INVALID", "OTP session is invalid. Please request a new OTP");
        }
    }

    private void logVendorFailure(GetOtpRequestEntity entity, String userId, SubmitOtpRequest request,
            VendorSubmitOtpRequest vendorRequest, VendorClient.VendorSubmitCallResult vendorCall,
            VendorSubmitOtpResponse vendorResponse, String errorCode, String errorMessage) {
        apiLogService.failure(entity.getRequestId(), userId, VendorApiOperation.SUBMIT_OTP, request,
                vendorRequest, vendorResponse, vendorCall.httpStatus(),
                vendorResponse == null ? null : vendorResponse.code(),
                vendorResponse == null ? null : vendorResponse.message(), errorCode, errorMessage);
    }

    private void handleVerificationFailure(GetOtpRequestEntity entity, String userId, SubmitOtpRequest request,
            VendorSubmitOtpRequest vendorRequest, VendorClient.VendorSubmitCallResult vendorCall,
            VendorSubmitOtpResponse vendorResponse, Instant now) {
        int attempts = entity.getOtpVerificationAttempts() + 1;
        entity.setOtpVerificationAttempts(attempts);
        entity.setUpdatedAt(now);
        int max = properties.otp().maxVerificationAttempts();
        boolean locked = attempts >= max;
        if (locked) {
            entity.setStatus(GetOtpStatus.OTP_LOCKED);
            entity.setOtpLockedUntil(now.plusSeconds(properties.otp().verificationLockSeconds()));
        } else {
            entity.setStatus(GetOtpStatus.OTP_VERIFY_FAILED);
        }
        repository.save(entity);

        Integer code = vendorResponse == null ? null : vendorResponse.code();
        String vendorMessage = vendorResponse == null ? null : vendorResponse.message();
        String errorCode = locked ? "OTP_ATTEMPTS_EXCEEDED" : "INVALID_OTP";
        String message = locked ? "Too many tries. Please try again later"
                : "Incorrect Code. " + Math.max(0, max - attempts) + " attempts left";

        apiLogService.failure(entity.getRequestId(), userId, VendorApiOperation.SUBMIT_OTP,
                request, vendorRequest, vendorResponse, vendorCall.httpStatus(), code, vendorMessage,
                errorCode, message);
        analytics.record("cs_otp_failed", entity.getRequestId(), userId, errorCode);
        throw new RequestValidationException(errorCode, message);
    }


    private String safeVendorMessage(String message) {
        if (message == null) return null;
        String sanitized = message.replaceAll("(?i)(otp|token|secret|pan|mobile|email)\\s*[:=]\\s*[^,; ]+", "$1=***");
        return sanitized.length() <= 250 ? sanitized : sanitized.substring(0, 250);
    }

}
