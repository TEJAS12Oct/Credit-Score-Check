package com.boltpe.creditscore.dto.response;

/**
 * BoltPe-owned response contract. Vendor-specific field names never leave the mapper.
 * creditReport contains the complete bureau payload normalized to BoltPe camelCase names.
 */
public record SubmitOtpResponse(
        String requestId,
        String status,
        Integer score,
        String band,
        String interpretation,
        ReportMetadata report,
        CreditSummary summary,
        boolean partialMatch,
        String cautionMessage,
        BoltPeCreditReport creditReport) {

    public record ReportMetadata(String reportDate, String reportTime, String reportNumber) {}

    public record CreditSummary(
            Integer totalAccounts,
            Integer activeAccounts,
            Integer closedAccounts,
            Long totalOutstanding,
            Integer recentEnquiries) {}
}
