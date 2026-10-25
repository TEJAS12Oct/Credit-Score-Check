package com.boltpe.creditscore.client.kycflow.dto.request;

public record VendorGetOtpRequest(
        String firstName,
        String lastname,
        String panNumber,
        String dob,
        String mobileNumber,
        String email,
        String gender,
        String pinCode,
        String consent) {
}
