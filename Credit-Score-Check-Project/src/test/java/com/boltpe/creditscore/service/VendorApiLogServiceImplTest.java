package com.boltpe.creditscore.service;

import com.boltpe.creditscore.entity.VendorApiLogEntity;
import com.boltpe.creditscore.enums.VendorApiOperation;
import com.boltpe.creditscore.repository.VendorApiLogRepository;
import com.boltpe.creditscore.service.impl.VendorApiLogServiceImpl;
import com.boltpe.creditscore.util.SensitiveDataMaskingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorApiLogServiceImplTest {

    @Test
    void successSanitizesSensitiveData() {
        VendorApiLogRepository repository = mock(VendorApiLogRepository.class);
        VendorApiLogServiceImpl service = new VendorApiLogServiceImpl(
                repository, new SensitiveDataMaskingUtil(new ObjectMapper()));

        service.success(
                "CS-1", "user-1", VendorApiOperation.GENERATE_OTP,
                Map.of("panNumber", "ABCDE1234F", "otp", "123456"),
                Map.of("success", true),
                Map.of("secretKey", "secret"),
                Map.of("token", "vendor-token"),
                200, 200, "OK");

        ArgumentCaptor<VendorApiLogEntity> captor = ArgumentCaptor.forClass(VendorApiLogEntity.class);
        verify(repository).save(captor.capture());

        VendorApiLogEntity log = captor.getValue();
        assertEquals("SUCCESS", log.getStatus());
        assertFalse(log.getBoltpeRequestBody().contains("ABCDE1234F"));
        assertFalse(log.getBoltpeRequestBody().contains("123456"));
        assertFalse(log.getVendorRequestBody().contains("\"secretKey\":\"secret\""));
        assertFalse(log.getVendorResponseBody().contains("vendor-token"));
        assertEquals(200, log.getVendorHttpStatus());
    }

    @Test
    void failureSupportsNullOptionalBodies() {
        VendorApiLogRepository repository = mock(VendorApiLogRepository.class);
        VendorApiLogServiceImpl service = new VendorApiLogServiceImpl(
                repository, new SensitiveDataMaskingUtil(new ObjectMapper()));

        service.failure(
                "CS-2", "user-2", VendorApiOperation.GENERATE_OTP,
                Map.of("mobileNumber", "9876543210"), null, null,
                503, 503, "Unavailable", "VENDOR_UNAVAILABLE", "Try again");

        ArgumentCaptor<VendorApiLogEntity> captor = ArgumentCaptor.forClass(VendorApiLogEntity.class);
        verify(repository).save(captor.capture());

        VendorApiLogEntity log = captor.getValue();
        assertEquals("FAILED", log.getStatus());
        assertNotNull(log.getBoltpeResponseBody());
        assertTrue(log.getBoltpeResponseBody().contains("VENDOR_UNAVAILABLE"));
        assertNull(log.getVendorRequestBody());
        assertNull(log.getVendorResponseBody());
        assertEquals("VENDOR_UNAVAILABLE", log.getErrorCode());
        assertEquals("Try again", log.getErrorMessage());
    }

    @Test
    void redactsSensitiveIdentifiersInPlainVendorMessages() {
        VendorApiLogRepository repository = mock(VendorApiLogRepository.class);
        VendorApiLogServiceImpl service = new VendorApiLogServiceImpl(
                repository, new SensitiveDataMaskingUtil(new ObjectMapper()));
        service.failure("CS-3", "user-3", VendorApiOperation.GENERATE_OTP,
                null, null, null, 400, 400,
                "PAN ABCDE1234F mobile 9876543210 email person@example.com otp=123456",
                "VENDOR_ERROR", "token=secret123");
        ArgumentCaptor<VendorApiLogEntity> captor = ArgumentCaptor.forClass(VendorApiLogEntity.class);
        verify(repository).save(captor.capture());
        VendorApiLogEntity saved = captor.getValue();
        assertFalse(saved.getVendorMessage().contains("ABCDE1234F"));
        assertFalse(saved.getVendorMessage().contains("9876543210"));
        assertFalse(saved.getVendorMessage().contains("person@example.com"));
        assertFalse(saved.getVendorMessage().contains("123456"));
        assertFalse(saved.getErrorMessage().contains("secret123"));
    }
}
