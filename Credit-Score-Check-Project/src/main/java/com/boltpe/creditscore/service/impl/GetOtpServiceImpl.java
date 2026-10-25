package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.client.kycflow.VendorClient;
import com.boltpe.creditscore.client.kycflow.dto.request.VendorGetOtpRequest;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorGetOtpResponse;
import com.boltpe.creditscore.config.GetOtpProperties;
import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.GetOtpResponse;
import com.boltpe.creditscore.entity.GetOtpRequestEntity;
import com.boltpe.creditscore.enums.VendorApiOperation;
import com.boltpe.creditscore.enums.GetOtpStatus;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.exception.VendorApiException;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import com.boltpe.creditscore.validation.GetOtpRequestValidator;
import com.boltpe.creditscore.repository.GetOtpRequestRepository;
import com.boltpe.creditscore.service.VendorApiLogService;
import com.boltpe.creditscore.service.ConsentService;
import com.boltpe.creditscore.service.GetOtpService;
import com.boltpe.creditscore.service.CreditScorePullLimitService;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class GetOtpServiceImpl implements GetOtpService {

    private static final String VENDOR_NAME = "KYC_FLOW";

    @PersistenceContext
    private EntityManager entityManager;

    private static final List<GetOtpStatus> ACTIVE_STATUSES = List.of(
            GetOtpStatus.CREATED,
            GetOtpStatus.OTP_SENT,
            GetOtpStatus.OTP_SEND_FAILED,
            GetOtpStatus.OTP_VERIFY_FAILED,
            GetOtpStatus.OTP_LOCKED);

    private final GetOtpRequestRepository requestRepository;
    private final ConsentService consentService;
    private final VendorApiLogService apiLogService;
    private final GetOtpVendorMapper vendorRequestMapper;
    private final VendorClient kycFlowClient;
    private final GetOtpProperties properties;
    private final GetOtpRequestValidator requestValidator;
    private final CreditScorePullLimitService pullLimitService;
    private final CreditScoreAnalyticsService analytics;

    public GetOtpServiceImpl(
            GetOtpRequestRepository requestRepository,
            ConsentService consentService,
            VendorApiLogService apiLogService,
            GetOtpVendorMapper vendorRequestMapper,
            VendorClient kycFlowClient,
            GetOtpProperties properties,
            GetOtpRequestValidator requestValidator, CreditScorePullLimitService pullLimitService, CreditScoreAnalyticsService analytics) {
        this.requestRepository = requestRepository;
        this.consentService = consentService;
        this.apiLogService = apiLogService;
        this.vendorRequestMapper = vendorRequestMapper;
        this.kycFlowClient = kycFlowClient;
        this.properties = properties;
        this.requestValidator = requestValidator;
        this.pullLimitService = pullLimitService;
        this.analytics = analytics;
    }

    @Override
    @Transactional
    public ApiResponse<GetOtpResponse> generateOtp(
            String userId,
            String registeredMobile,
            GetOtpRequest request) {
        requestValidator.validate(userId, registeredMobile, request);
        // Transaction-scoped advisory lock serializes OTP send/resend per user across service replicas.
        if (entityManager != null) {
            entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:userId))")
                    .setParameter("userId", "credit-score-otp:" + userId).getSingleResult();
        }
        pullLimitService.validateAllowed(userId);

        Instant now = Instant.now();
        GetOtpRequestEntity creditScoreRequest = findOrCreateRequest(userId, now);
        VendorGetOtpRequest vendorRequest = vendorRequestMapper.toVendor(request);

        VendorClient.VendorCallResult vendorCall;
        try {
            vendorCall = kycFlowClient.generateOtp(vendorRequest);
        } catch (VendorApiException exception) {
            markOtpFailure(creditScoreRequest);
            apiLogService.failure(
                    creditScoreRequest.getRequestId(),
                    userId,
                    VendorApiOperation.GENERATE_OTP,
                    request,
                    vendorRequest,
                    null,
                    null,
                    null,
                    null,
                    exception.getCode(),
                    exception.getMessage());
            throw exception;
        }

        VendorGetOtpResponse vendorResponse = vendorCall.body();
        if (vendorResponse == null || !vendorResponse.isSuccess()) {
            handleVendorFailure(
                    creditScoreRequest,
                    userId,
                    request,
                    vendorRequest,
                    vendorCall,
                    vendorResponse);
        }

        return handleVendorSuccess(
                creditScoreRequest,
                userId,
                request,
                vendorRequest,
                vendorCall,
                vendorResponse);
    }

    private GetOtpRequestEntity findOrCreateRequest(String userId, Instant now) {
        requestRepository.findTopByUserIdAndOtpLockedUntilAfterOrderByOtpLockedUntilDesc(userId, now)
                .ifPresent(locked -> {
                    throw new RequestValidationException(
                            "OTP_ATTEMPTS_EXCEEDED", "Too many tries. Please try again later");
                });

        Instant sessionStart = now.minusSeconds(properties.otp().sessionValiditySeconds());

        GetOtpRequestEntity existingRequest = requestRepository
                .findTopByUserIdAndStatusInAndRequestedAtAfterOrderByRequestedAtDesc(
                        userId,
                        ACTIVE_STATUSES,
                        sessionStart)
                .orElse(null);

        if (existingRequest != null) {
            validateOtpResend(existingRequest, now);
            return existingRequest;
        }

        GetOtpRequestEntity newRequest = GetOtpRequestEntity.builder()
                .requestId(generateRequestId())
                .userId(userId)
                .status(GetOtpStatus.CREATED)
                .vendor(VENDOR_NAME)
                .otpSendCount(0)
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        GetOtpRequestEntity savedRequest = requestRepository.save(newRequest);
        consentService.record(savedRequest.getRequestId(), userId);
        analytics.record("cs_consent_given", savedRequest.getRequestId(), userId, "RECORDED");
        return savedRequest;
    }

    private ApiResponse<GetOtpResponse> handleVendorSuccess(
            GetOtpRequestEntity requestEntity,
            String userId,
            GetOtpRequest boltpeRequest,
            VendorGetOtpRequest vendorRequest,
            VendorClient.VendorCallResult vendorCall,
            VendorGetOtpResponse vendorResponse) {
        int otpSendCount = requestEntity.getOtpSendCount() + 1;
        Instant now = Instant.now();

        requestEntity.setStatus(GetOtpStatus.OTP_SENT);
        requestEntity.setVendorRequestId(vendorResponse.result().sendOtpResponse().requestId());
        requestEntity.setVendorToken(vendorResponse.result().token());
        requestEntity.setOtpSendCount(otpSendCount);
        requestEntity.setLastOtpSentAt(now);
        requestEntity.setUpdatedAt(now);
        requestRepository.save(requestEntity);

        GetOtpResponse data = new GetOtpResponse(
                requestEntity.getRequestId(),
                true,
                properties.otp().resendCooldownSeconds(),
                Math.max(0, properties.otp().maxOtpSends() - otpSendCount));

        ApiResponse<GetOtpResponse> response =
                ApiResponse.ok("OTP_SENT", "OTP sent successfully", data);

        analytics.record("cs_otp_requested", requestEntity.getRequestId(), userId, "OTP_SENT");

        apiLogService.success(
                requestEntity.getRequestId(),
                userId,
                VendorApiOperation.GENERATE_OTP,
                boltpeRequest,
                response,
                vendorRequest,
                vendorResponse,
                vendorCall.httpStatus(),
                vendorResponse.code(),
                vendorResponse.message());

        return response;
    }

    private void handleVendorFailure(
            GetOtpRequestEntity requestEntity,
            String userId,
            GetOtpRequest boltpeRequest,
            VendorGetOtpRequest vendorRequest,
            VendorClient.VendorCallResult vendorCall,
            VendorGetOtpResponse vendorResponse) {
        markOtpFailure(requestEntity);

        Integer vendorCode = vendorResponse == null ? null : vendorResponse.code();
        String vendorMessage = vendorResponse == null ? null : vendorResponse.message();
        String errorCode = mapVendorCode(vendorCode);
        String errorMessage = userMessage(vendorCode);

        apiLogService.failure(
                requestEntity.getRequestId(),
                userId,
                VendorApiOperation.GENERATE_OTP,
                boltpeRequest,
                vendorRequest,
                vendorResponse,
                vendorCall.httpStatus(),
                vendorCode,
                vendorMessage,
                errorCode,
                errorMessage);

        analytics.record("cs_error", requestEntity.getRequestId(), userId, errorCode);
        throw new VendorApiException(errorCode, errorMessage);
    }

    private void markOtpFailure(GetOtpRequestEntity request) {
        request.setStatus(GetOtpStatus.OTP_SEND_FAILED);
        request.setUpdatedAt(Instant.now());
        requestRepository.save(request);

    }

    private void validateOtpResend(GetOtpRequestEntity request, Instant now) {
        if (request.getOtpSendCount() >= properties.otp().maxOtpSends()) {
            throw new RequestValidationException(
                    "OTP_SEND_LIMIT_EXCEEDED",
                    "Maximum OTP send limit reached");
        }

        Instant lastOtpSentAt = request.getLastOtpSentAt();
        if (lastOtpSentAt == null) {
            return;
        }

        long elapsedSeconds = Duration.between(lastOtpSentAt, now).getSeconds();
        long cooldownSeconds = properties.otp().resendCooldownSeconds();

        if (elapsedSeconds < cooldownSeconds) {
            long remainingSeconds = cooldownSeconds - Math.max(0, elapsedSeconds);
            throw new RequestValidationException(
                    "OTP_RESEND_COOLDOWN",
                    "Please wait " + remainingSeconds + " seconds before requesting another OTP");
        }
    }

    private String mapVendorCode(Integer vendorCode) {
        if (vendorCode == null) {
            return "VENDOR_INVALID_RESPONSE";
        }

        return switch (vendorCode) {
            case 400 -> "INVALID_REQUEST_DATA";
            case 402 -> "CREDIT_SCORE_SERVICE_UNAVAILABLE";
            case 404 -> "CREDIT_SCORE_DATA_NOT_FOUND";
            case 429 -> "VENDOR_RATE_LIMITED";
            default -> "CREDIT_SCORE_SERVICE_UNAVAILABLE";
        };
    }

    private String userMessage(Integer vendorCode) {
        if (Integer.valueOf(400).equals(vendorCode)) {
            return "Invalid request data. Please check the details and try again.";
        }

        if (Integer.valueOf(402).equals(vendorCode)) {
            return "Credit score service is temporarily unavailable. Please try again later.";
        }

        if (Integer.valueOf(404).equals(vendorCode)) {
            return "Requested credit score data could not be found.";
        }

        if (Integer.valueOf(429).equals(vendorCode)) {
            return "Credit score service is busy. Please try again later.";
        }

        return "Couldn’t send OTP. Please try again later.";
    }

    private String generateRequestId() {
        String randomValue = UUID.randomUUID().toString().replace("-", "");
        return "CS-" + randomValue.substring(0, 16).toUpperCase();
    }
}
