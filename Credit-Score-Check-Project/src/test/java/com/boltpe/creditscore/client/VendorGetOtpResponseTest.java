package com.boltpe.creditscore.client;

import static org.junit.jupiter.api.Assertions.*;

import com.boltpe.creditscore.client.kycflow.dto.response.VendorGetOtpResponse;

import java.util.List;

import org.junit.jupiter.api.Test;

class VendorGetOtpResponseTest {
    @Test
    void successNeeds200TrueAndToken() {
        var s = new VendorGetOtpResponse.SendOtpResponse(true, "r", List.of("ok"));
        assertTrue(new VendorGetOtpResponse(200, "ok", new VendorGetOtpResponse.Result(s, "t")).isSuccess());
        assertFalse(new VendorGetOtpResponse(200, "ok", new VendorGetOtpResponse.Result(s, "")).isSuccess());
        assertFalse(new VendorGetOtpResponse(400, "bad", null).isSuccess());
    }
}
