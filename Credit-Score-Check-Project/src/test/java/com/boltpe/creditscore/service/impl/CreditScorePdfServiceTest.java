package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.dto.response.BoltPeCreditReport;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreditScorePdfServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CreditScorePdfService service = new CreditScorePdfService();

    @Test void generatesValidPdfWhenOptionalSectionsAreMissing() throws Exception {
        SubmitOtpResponse response = new SubmitOtpResponse("r", "SUCCESS", 841, "EXCELLENT", "Good", null, null, false, null, null);
        byte[] pdf = service.render(response);
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(pdf)) { assertTrue(document.getNumberOfPages() >= 3); }
    }

    @Test void generatesMultiPagePdfWithAccountsHistoryAndMissingValues() throws Exception {
        JsonNode application = mapper.readTree("""
            {"currentApplicationDetails":{"currentApplicantDetails":{"firstName":"Test","lastName":"User","genderCode":"1","dateOfBirthApplicant":"19900101","incomeTaxPan":"XXXXX1234X","mobilePhoneNumber":"XXXXXX1234","emailId":"t***@***"},"currentApplicantAddressDetails":{"state":"27","pinCode":"411001"}}}
            """);
        JsonNode summary = mapper.readTree("""
            {
              "totalOutstandingBalance": {
                "outstandingBalanceSecured": "10000",
                "outstandingBalanceUnSecured": "25000"
              },
              "creditAccount": {
                "creditAccountDefault": "0"
              }
            }
            """);
        JsonNode accounts = mapper.readTree("""
          [{"subscriberName":"Example Bank","accountStatus":"13","accountNumber":"XXXXXXXX1234","creditLimitAmount":"50000","highestCreditOrOriginalLoanAmount":"50000","currentBalance":"10000","amountPastDue":"0","rateOfInterest":"12","currencyCode":"INR","openDate":"20240101","dateReported":"20261001","caisHolderAddressDetails":[{"zipPostalCodeNonNormalized":"411001"}],"caisAccountHistory":[{"year":"2026","month":"9","daysPastDue":"0"},{"year":"2026","month":"8","daysPastDue":"30"}]},{"subscriberName":"Other Bank","accountStatus":"11","accountNumber":"XXXX1234","creditLimitAmount":"70000","currentBalance":"25000","caisHolderIdDetails":[{"incomeTaxPan":"XXXXX1234X"}]}]
          """);
        BoltPeCreditReport report = new BoltPeCreditReport(null, new BoltPeCreditReport.Application(application),
                new BoltPeCreditReport.CreditAccounts(summary, accounts), new BoltPeCreditReport.MatchInformation("Y"),
                new BoltPeCreditReport.EnquirySummary(1,2,3,4), null,null,null,null,null);
        SubmitOtpResponse response = new SubmitOtpResponse("r", "SUCCESS", 841, "EXCELLENT", "Good",
                new SubmitOtpResponse.ReportMetadata("20261012", "12:00", "12345"),
                new SubmitOtpResponse.CreditSummary(2,1,1,35000L,4),false,null,report);
        byte[] pdf = service.render(response);
        try (var document = Loader.loadPDF(pdf)) { assertTrue(document.getNumberOfPages() >= 3); }
    }

    @Test void handlesNullScoreAndEmptyCreditReport() throws Exception {
        SubmitOtpResponse response = new SubmitOtpResponse("missing", "SUCCESS", null, null, null,
                new SubmitOtpResponse.ReportMetadata(null, null, null),
                new SubmitOtpResponse.CreditSummary(null, null, null, null, null),
                true, "partial", null);
        byte[] bytes = service.render(response);
        try (var document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= 3);
        }
    }

    @Test void masksUnexpectedUnmaskedIdentifiersInGeneratedPdf() throws Exception {
        JsonNode application = mapper.readTree("""
                {"currentApplicationDetails":{"currentApplicantDetails":{
                  "firstName":"Sample","lastName":"Person","incomeTaxPan":"ABCDE1234F",
                  "mobilePhoneNumber":"9876543210","emailId":"secret.person@example.com"
                },"currentApplicantAddressDetails":{"pinCode":"411001"}}}
                """);
        BoltPeCreditReport report = new BoltPeCreditReport(null,
                new BoltPeCreditReport.Application(application), null, null, null,
                null, null, null, null, null);
        SubmitOtpResponse response = new SubmitOtpResponse("mask", "SUCCESS", 780, "GOOD", "ok",
                null, null, false, null, report);
        byte[] bytes = service.render(response);
        try (var document = Loader.loadPDF(bytes)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertFalse(text.contains("ABCDE1234F"));
            assertFalse(text.contains("9876543210"));
            assertFalse(text.contains("secret.person@example.com"));
            assertTrue(text.contains("Sample Person"));
        }
    }
    @Test void masksUnmaskedCreditAccountNumbersAndPanContainingSingleX() throws Exception {
        JsonNode application = mapper.readTree("""
                {"currentApplicationDetails":{"currentApplicantDetails":{
                  "incomeTaxPan":"ABCDX1234F", "emailId":"alex@example.com"
                }}}
                """);
        JsonNode accounts = mapper.readTree("""
                [{"subscriberName":"Example Bank","accountNumber":"1234567890123456",
                  "creditLimitAmount":"20000"}]
                """);
        BoltPeCreditReport report = new BoltPeCreditReport(null,
                new BoltPeCreditReport.Application(application),
                new BoltPeCreditReport.CreditAccounts(null, accounts), null, null,
                null, null, null, null, null);
        SubmitOtpResponse response = new SubmitOtpResponse("masked", "SUCCESS", 800,
                "GOOD", "ok", null, null, false, null, report);
        try (var document = Loader.loadPDF(service.render(response))) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertFalse(text.contains("ABCDX1234F"));
            assertFalse(text.contains("1234567890123456"));
            assertFalse(text.contains("alex@example.com"));
            assertTrue(text.contains("****3456"));
        }
    }

    @Test void displaysFullNameMaskedEmailAndIstTimestamp() throws Exception {
        JsonNode application = mapper.readTree("""
                {"currentApplicationDetails":{"currentApplicantDetails":{
                  "firstName":"Tejas","middleName1":"Satish","lastName":"Jawale",
                  "emailId":"tejas@example.com"
                }}}
                """);
        BoltPeCreditReport report = new BoltPeCreditReport(null,
                new BoltPeCreditReport.Application(application), null, null, null,
                null, null, null, null, null);
        SubmitOtpResponse response = new SubmitOtpResponse("r", "SUCCESS", 837, "EXCELLENT", "Excellent",
                null, null, false, null, report);
        try (var document = Loader.loadPDF(service.render(response))) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("Tejas Satish Jawale"));
            assertTrue(text.contains("t****@****.com"));
            assertFalse(text.contains("tejas@example.com"));
            assertTrue(text.contains("Generated on (IST):"));
            assertFalse(text.contains("Generated on (UTC):"));
        }
    }

    @Test void currencyValuesRenderWithVectorRupeeAndNumericAmount() throws Exception {
        SubmitOtpResponse response = new SubmitOtpResponse("currency", "SUCCESS", 760,
                "GOOD", "ok", null,
                new SubmitOtpResponse.CreditSummary(1, 1, 0, 125000L, 0),
                false, null, null);
        try (var document = Loader.loadPDF(service.render(response))) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            // The rupee glyph is drawn as vector paths, so PDFTextStripper cannot extract it.
            // Assert the numeric amount rather than expecting the old textual INR fallback.
            assertTrue(text.contains("125,000"));
            assertFalse(text.contains("INR 125,000"));
        }
    }
}
