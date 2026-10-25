package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.entity.VendorApiLogEntity;
import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.enums.VendorApiOperation;
import com.boltpe.creditscore.repository.VendorApiLogRepository;
import com.boltpe.creditscore.service.VendorApiLogService;
import com.boltpe.creditscore.util.SensitiveDataMaskingUtil;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class VendorApiLogServiceImpl implements VendorApiLogService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final VendorApiLogRepository repository;
    private final SensitiveDataMaskingUtil maskingUtil;

    public VendorApiLogServiceImpl(
            VendorApiLogRepository repository,
            SensitiveDataMaskingUtil maskingUtil) {
        this.repository = repository;
        this.maskingUtil = maskingUtil;
    }

    @Override
    public void success(
            String requestId,
            String userId,
            VendorApiOperation operation,
            Object boltpeRequest,
            Object boltpeResponse,
            Object vendorRequest,
            Object vendorResponse,
            Integer httpStatus,
            Integer vendorCode,
            String vendorMessage) {
        save(
                requestId,
                userId,
                operation,
                boltpeRequest,
                boltpeResponse,
                vendorRequest,
                vendorResponse,
                httpStatus,
                vendorCode,
                vendorMessage,
                STATUS_SUCCESS,
                null,
                null);
    }

    @Override
    public void failure(
            String requestId,
            String userId,
            VendorApiOperation operation,
            Object boltpeRequest,
            Object vendorRequest,
            Object vendorResponse,
            Integer httpStatus,
            Integer vendorCode,
            String vendorMessage,
            String errorCode,
            String errorMessage) {
        save(
                requestId,
                userId,
                operation,
                boltpeRequest,
                ApiResponse.fail(errorCode, errorMessage),
                vendorRequest,
                vendorResponse,
                httpStatus,
                vendorCode,
                vendorMessage,
                STATUS_FAILED,
                errorCode,
                errorMessage);
    }

    private void save(
            String requestId,
            String userId,
            VendorApiOperation operation,
            Object boltpeRequest,
            Object boltpeResponse,
            Object vendorRequest,
            Object vendorResponse,
            Integer httpStatus,
            Integer vendorCode,
            String vendorMessage,
            String status,
            String errorCode,
            String errorMessage) {
        VendorApiLogEntity log = VendorApiLogEntity.builder()
                .requestId(requestId)
                .userId(userId)
                .operation(operation)
                .boltpeRequestBody(maskingUtil.sanitize(boltpeRequest))
                .boltpeResponseBody(sanitizeNullable(boltpeResponse))
                .vendorRequestBody(sanitizeNullable(vendorRequest))
                .vendorResponseBody(sanitizeNullable(vendorResponse))
                .vendorHttpStatus(httpStatus)
                .vendorCode(vendorCode)
                .vendorMessage(maskingUtil.sanitizeFreeText(vendorMessage))
                .status(status)
                .errorCode(errorCode)
                .errorMessage(maskingUtil.sanitizeFreeText(errorMessage))
                .createdAt(Instant.now())
                .build();

        repository.save(log);
    }

    private String sanitizeNullable(Object value) {
        return value == null ? null : maskingUtil.sanitize(value);
    }
}
