package com.boltpe.creditscore.service;

import com.boltpe.creditscore.enums.VendorApiOperation;

public interface VendorApiLogService {

    void success(
            String requestId,
            String userId,
            VendorApiOperation operation,
            Object boltpeRequest,
            Object boltpeResponse,
            Object vendorRequest,
            Object vendorResponse,
            Integer httpStatus,
            Integer vendorCode,
            String vendorMessage);

    void failure(
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
            String errorMessage);
}
