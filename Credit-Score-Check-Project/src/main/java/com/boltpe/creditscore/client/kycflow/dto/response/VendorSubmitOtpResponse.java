package com.boltpe.creditscore.client.kycflow.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

public record VendorSubmitOtpResponse(Integer code, String message, JsonNode result) {
    public boolean isSuccess() {
        return Integer.valueOf(200).equals(code) && result != null && !result.isNull();
    }
}
