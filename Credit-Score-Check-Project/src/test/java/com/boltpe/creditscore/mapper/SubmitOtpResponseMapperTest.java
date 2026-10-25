package com.boltpe.creditscore.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SubmitOtpResponseMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SubmitOtpResponseMapper responseMapper = new SubmitOtpResponseMapper();

    @Test
    void mapsScoreMetadataSummaryAndPartialMatch() throws Exception {
        var result = mapper.readTree("""
            {"SCORE":{"FCIREXScore":774},"CreditProfileHeader":{"ReportDate":"20240613","ReportTime":"175220","ReportNumber":"1718281340692"},
             "CAIS_Account":{"CAIS_Summary":{"Credit_Account":{"CreditAccountTotal":2,"CreditAccountActive":1,"CreditAccountClosed":1},"Total_Outstanding_Balance":{"Outstanding_Balance_All":55143}},"CAIS_Account_DETAILS":[{}]},
             "TotalCAPS_Summary":{"TotalCAPSLast30Days":3},"Match_result":{"Exact_match":"N"}}
            """);
        SubmitOtpResponse r = responseMapper.toResponse("CSR-1", result);
        assertEquals(774, r.score());
        assertEquals("VERY_GOOD", r.band());
        assertEquals(2, r.summary().totalAccounts());
        assertEquals(55143L, r.summary().totalOutstanding());
        assertTrue(r.partialMatch());
        assertNotNull(r.cautionMessage());
    }

    @Test void mapsNoHit() throws Exception {
        SubmitOtpResponse r = responseMapper.toResponse("CSR-1", mapper.readTree("{}"));
        assertEquals("NO_HIT", r.status());
        assertNull(r.score());
    }

    @Test void mapsAllBandBoundaries() {
        assertEquals("POOR", responseMapper.bandFor(300).label());
        assertEquals("POOR", responseMapper.bandFor(579).label());
        assertEquals("FAIR", responseMapper.bandFor(580).label());
        assertEquals("FAIR", responseMapper.bandFor(669).label());
        assertEquals("GOOD", responseMapper.bandFor(670).label());
        assertEquals("GOOD", responseMapper.bandFor(739).label());
        assertEquals("VERY_GOOD", responseMapper.bandFor(740).label());
        assertEquals("VERY_GOOD", responseMapper.bandFor(799).label());
        assertEquals("EXCELLENT", responseMapper.bandFor(800).label());
        assertEquals("EXCELLENT", responseMapper.bandFor(900).label());
        assertEquals("UNAVAILABLE", responseMapper.bandFor(299).label());
        assertEquals("UNAVAILABLE", responseMapper.bandFor(901).label());
    }

    @Test
    void mapsVendorSectionsToBoltPeOwnedDomainContract() throws Exception {
        var result = mapper.readTree("""
            {"CreditProfileHeader":{"ReportNumber":"R1"},
             "CAIS_Account":{"CAIS_Summary":{"Credit_Account":{"CreditAccountTotal":"1"}},
                             "CAIS_Account_DETAILS":[{"PAN_Issue_Date":"","ZIP_Postal_Code_non_normalized":"411021"}]},
             "TotalCAPS_Summary":{"TotalCAPSLast7Days":"1","TotalCAPSLast30Days":"2"},
             "SCORE":{"FCIREXScore":837,"FCIREXScoreConfidLevel":"HIGH"}}
            """);
        SubmitOtpResponse response = responseMapper.toResponse("CSR-1", result);
        assertEquals(837, response.creditReport().scoreDetails().score());
        assertEquals(2, response.creditReport().enquirySummary().last30Days());
        String json = mapper.writeValueAsString(response.creditReport());
        assertTrue(json.contains("\"creditAccounts\""));
        assertTrue(json.contains("\"panIssueDate\""));
        assertTrue(json.contains("\"zipPostalCodeNonNormalized\""));
        assertFalse(json.contains("CAIS_Account"));
        assertFalse(json.contains("fCIREXScore"));
    }
}
