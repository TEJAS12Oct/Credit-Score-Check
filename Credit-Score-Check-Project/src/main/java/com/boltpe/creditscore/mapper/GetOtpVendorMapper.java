package com.boltpe.creditscore.mapper;

import com.boltpe.creditscore.client.kycflow.dto.request.VendorGetOtpRequest;
import com.boltpe.creditscore.dto.request.GetOtpRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Component;

@Component
public class GetOtpVendorMapper {

    public static final String CONSENT_TEXT =
            "I explicitly consent to the collection, processing, and verification of my data "
                    + "for authentication, KYC, and compliance purposes.";

    private static final DateTimeFormatter BOLTPE_DOB_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter VENDOR_DOB_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMuuuu");

    public VendorGetOtpRequest toVendor(GetOtpRequest request) {
        LocalDate dob = parseDob(request.dob());

        return new VendorGetOtpRequest(
                request.firstName().trim(),
                request.lastName().trim(),
                request.panNumber().toUpperCase(),
                dob.format(VENDOR_DOB_FORMATTER),
                normalizeMobile(request.mobileNumber()),
                request.email().trim(),
                request.gender().trim(),
                request.pinCode(),
                request.consent().trim());
    }

    public LocalDate parseDob(String dob) {
        return LocalDate.parse(dob, BOLTPE_DOB_FORMATTER);
    }

    private String normalizeMobile(String mobileNumber) {
        return mobileNumber.startsWith("+91")
                ? mobileNumber.substring(3)
                : mobileNumber;
    }
}
