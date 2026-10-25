package com.boltpe.creditscore.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetOtpRequest(
        String firstName,
        @JsonProperty("lastname") String lastName,
        String panNumber,
        String dob,
        String mobileNumber,
        String email,
        String gender,
        String pinCode,
        String consent) {
}
