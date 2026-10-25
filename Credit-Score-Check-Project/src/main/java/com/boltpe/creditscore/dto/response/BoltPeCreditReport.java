package com.boltpe.creditscore.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * BoltPe-owned credit report contract.
 *
 * The section names below are BoltPe domain names and are intentionally independent
 * from the upstream bureau/vendor wrapper. Vendor adapters/mappers are responsible
 * for translating their payload into this contract.
 *
 * Section payloads remain JSON objects because bureau schemas contain a very large,
 * evolving set of optional attributes. This keeps forward compatibility without
 * exposing vendor section names at the API boundary. All nested keys are normalized
 * by SubmitOtpResponseMapper before they are assigned here.
 */
public record BoltPeCreditReport(
        CreditProfile creditProfile,
        Application application,
        CreditAccounts creditAccounts,
        MatchInformation matchInformation,
        EnquirySummary enquirySummary,
        CreditEnquiries creditEnquiries,
        NonCreditEnquiries nonCreditEnquiries,
        ScoreDetails scoreDetails,
        JsonNode header,
        JsonNode userMessage) {

    public record CreditProfile(JsonNode details) {}
    public record Application(JsonNode details) {}
    public record CreditAccounts(JsonNode summary, JsonNode accounts) {}
    public record MatchInformation(String exactMatch) {}
    public record EnquirySummary(Integer last7Days, Integer last30Days, Integer last90Days, Integer last180Days) {}
    public record CreditEnquiries(JsonNode summary, JsonNode applications) {}
    public record NonCreditEnquiries(JsonNode summary, JsonNode applications) {}
    public record ScoreDetails(Integer score, String confidenceLevel) {}
}
