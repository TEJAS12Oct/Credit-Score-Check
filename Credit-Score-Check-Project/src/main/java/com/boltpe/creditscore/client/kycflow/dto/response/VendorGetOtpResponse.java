package com.boltpe.creditscore.client.kycflow.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VendorGetOtpResponse(Integer code, String message, Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("sendOtpResponse") SendOtpResponse sendOtpResponse,
            String token) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendOtpResponse(
            @JsonProperty("return") Boolean success,
            @JsonProperty("request_id") String requestId,
            List<String> message) {
    }

    public boolean isSuccess() {
        return Integer.valueOf(200).equals(code)
                && result != null
                && result.sendOtpResponse() != null
                && Boolean.TRUE.equals(result.sendOtpResponse().success())
                && result.token() != null
                && !result.token().isBlank();
    }
}
