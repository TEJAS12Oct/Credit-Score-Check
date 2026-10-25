package com.boltpe.creditscore.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.boltpe.creditscore.dto.request.GetOtpRequest;

import org.junit.jupiter.api.Test;

class GetOtpVendorMapperTest {
    @Test
    void mapsBoltPeToVendorContract() {
        var r = new GetOtpRequest(" Tejas ", " Jawale ", "ABCDE1234F", "09-05-1995", "9876543210", "a@b.com", "male", "411001", GetOtpVendorMapper.CONSENT_TEXT);
        var v = new GetOtpVendorMapper().toVendor(r);
        assertEquals("Tejas", v.firstName());
        assertEquals("Jawale", v.lastname());
        assertEquals("09051995", v.dob());
        assertEquals(GetOtpVendorMapper.CONSENT_TEXT, v.consent());
    }
}
