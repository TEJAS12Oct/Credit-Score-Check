package com.boltpe.creditscore.mapper;

import com.boltpe.creditscore.dto.response.BoltPeCreditReport;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubmitOtpResponseMapper {

    public SubmitOtpResponse toResponse(String requestId, JsonNode result) {
        Integer score = integerAt(result, "/SCORE/FCIREXScore");
        boolean hasAccounts = result.at("/CAIS_Account/CAIS_Account_DETAILS").isArray()
                && !result.at("/CAIS_Account/CAIS_Account_DETAILS").isEmpty();
        boolean noHit = score == null && !hasAccounts;
        String exactMatch = textAt(result, "/Match_result/Exact_match");
        boolean partialMatch = "N".equalsIgnoreCase(exactMatch);

        SubmitOtpResponse.ReportMetadata metadata = new SubmitOtpResponse.ReportMetadata(
                textAt(result, "/CreditProfileHeader/ReportDate"),
                textAt(result, "/CreditProfileHeader/ReportTime"),
                textAt(result, "/CreditProfileHeader/ReportNumber"));

        SubmitOtpResponse.CreditSummary summary = new SubmitOtpResponse.CreditSummary(
                integerAt(result, "/CAIS_Account/CAIS_Summary/Credit_Account/CreditAccountTotal"),
                integerAt(result, "/CAIS_Account/CAIS_Summary/Credit_Account/CreditAccountActive"),
                integerAt(result, "/CAIS_Account/CAIS_Summary/Credit_Account/CreditAccountClosed"),
                longAt(result, "/CAIS_Account/CAIS_Summary/Total_Outstanding_Balance/Outstanding_Balance_All"),
                integerAt(result, "/TotalCAPS_Summary/TotalCAPSLast30Days"));

        ScoreBand band = score == null ? null : bandFor(score);
        return new SubmitOtpResponse(
                requestId,
                noHit ? "NO_HIT" : "SUCCESS",
                score,
                band == null ? null : band.label(),
                band == null ? null : band.meaning(),
                metadata,
                summary,
                partialMatch,
                partialMatch ? "Credit profile may be a partial or near identity match." : null,
                toBoltPeCreditReport(result));
    }

    /** Maps the vendor/bureau result to BoltPe-owned domain sections. */
    BoltPeCreditReport toBoltPeCreditReport(JsonNode result) {
        JsonNode emptyObject = JsonNodeFactory.instance.objectNode();
        JsonNode emptyArray = JsonNodeFactory.instance.arrayNode();
        if (result == null || result.isNull() || result.isMissingNode()) {
            return new BoltPeCreditReport(
                    new BoltPeCreditReport.CreditProfile(emptyObject),
                    new BoltPeCreditReport.Application(emptyObject),
                    new BoltPeCreditReport.CreditAccounts(emptyObject, emptyArray),
                    new BoltPeCreditReport.MatchInformation(null),
                    new BoltPeCreditReport.EnquirySummary(null, null, null, null),
                    new BoltPeCreditReport.CreditEnquiries(emptyObject, emptyArray),
                    new BoltPeCreditReport.NonCreditEnquiries(emptyObject, emptyArray),
                    new BoltPeCreditReport.ScoreDetails(null, null), emptyObject, emptyObject);
        }

        JsonNode cais = result.path("CAIS_Account");
        JsonNode caps = result.path("CAPS");
        JsonNode nonCreditCaps = result.path("NonCreditCAPS");

        return new BoltPeCreditReport(
                new BoltPeCreditReport.CreditProfile(normalizeNode(result.path("CreditProfileHeader"))),
                new BoltPeCreditReport.Application(normalizeNode(result.path("Current_Application"))),
                new BoltPeCreditReport.CreditAccounts(
                        normalizeNode(cais.path("CAIS_Summary")),
                        normalizeNode(cais.path("CAIS_Account_DETAILS"))),
                new BoltPeCreditReport.MatchInformation(textAt(result, "/Match_result/Exact_match")),
                new BoltPeCreditReport.EnquirySummary(
                        integerAt(result, "/TotalCAPS_Summary/TotalCAPSLast7Days"),
                        integerAt(result, "/TotalCAPS_Summary/TotalCAPSLast30Days"),
                        integerAt(result, "/TotalCAPS_Summary/TotalCAPSLast90Days"),
                        integerAt(result, "/TotalCAPS_Summary/TotalCAPSLast180Days")),
                new BoltPeCreditReport.CreditEnquiries(
                        normalizeNode(caps.path("CAPS_Summary")),
                        normalizeNode(caps.path("CAPS_Application_Details"))),
                new BoltPeCreditReport.NonCreditEnquiries(
                        normalizeNode(nonCreditCaps.path("NonCreditCAPS_Summary")),
                        normalizeNode(nonCreditCaps.path("CAPS_Application_Details"))),
                new BoltPeCreditReport.ScoreDetails(
                        integerAt(result, "/SCORE/FCIREXScore"),
                        textAt(result, "/SCORE/FCIREXScoreConfidLevel")),
                normalizeNode(result.path("Header")),
                normalizeNode(result.path("UserMessage")));
    }

    private JsonNode normalizeNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return JsonNodeFactory.instance.nullNode();
        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String boltPeName = toBoltPeFieldName(field.getKey());
                out.set(boltPeName, normalizeSensitiveNode(boltPeName, field.getValue()));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> out.add(normalizeNode(value)));
            return out;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value == null || value.isBlank() || "undefined".equalsIgnoreCase(value) || "?".equals(value)) {
                return JsonNodeFactory.instance.textNode("Not Reported");
            }
        }
        return node.deepCopy();
    }

    private JsonNode normalizeSensitiveNode(String fieldName, JsonNode value) {
        JsonNode normalized = normalizeNode(value);
        if (normalized == null || !normalized.isTextual()) return normalized;
        String text = normalized.asText();
        if ("Not Reported".equals(text)) return normalized;
        String key = fieldName == null ? "" : fieldName.toLowerCase();
        if (key.contains("pan")) return JsonNodeFactory.instance.textNode(maskKeepLast(text, 4));
        if (key.contains("accountnumber")) return JsonNodeFactory.instance.textNode(maskKeepLast(text, 4));
        if (key.contains("mobile") || key.contains("telephone")) return JsonNodeFactory.instance.textNode(maskKeepLast(text, 4));
        if (key.contains("email")) return JsonNodeFactory.instance.textNode(maskEmail(text));
        return normalized;
    }

    private String maskKeepLast(String value, int keep) {
        if (value == null || value.length() <= keep) return "****";
        return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        return value.substring(0, 1) + "***" + value.substring(at);
    }

    private String toBoltPeFieldName(String value) {
        if (value == null || value.isBlank()) return value;
        String normalized = value
                .replace("CAIS", "Cais")
                .replace("CAPS", "Caps")
                .replace("FCIREX", "Fcirex")
                .replace("PAN", "Pan")
                .replace("PIN", "Pin")
                .replace("TAX", "Tax")
                .replace("ZIP", "Zip")
                .replace("EMail", "Email")
                .replace("ID", "Id");
        StringBuilder out = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '_' || c == '-' || Character.isWhitespace(c)) {
                upperNext = out.length() > 0;
                continue;
            }
            if (out.length() == 0) out.append(Character.toLowerCase(c));
            else if (upperNext) { out.append(Character.toUpperCase(c)); upperNext = false; }
            else out.append(c);
        }
        return out.toString();
    }

    ScoreBand bandFor(int score) {
        if (score < 300 || score > 900) return new ScoreBand("UNAVAILABLE", "Credit score band is unavailable.");
        if (score <= 579) return new ScoreBand("POOR", "Limited or troubled history; approvals may be harder and rates higher.");
        if (score <= 669) return new ScoreBand("FAIR", "Below average; some approvals may be on stricter terms.");
        if (score <= 739) return new ScoreBand("GOOD", "Acceptable to most lenders with reasonable terms.");
        if (score <= 799) return new ScoreBand("VERY_GOOD", "Strong profile with good approval odds and rates.");
        return new ScoreBand("EXCELLENT", "Top tier with the best approval odds and pricing.");
    }

    private String textAt(JsonNode root, String pointer) {
        JsonNode node = root == null ? null : root.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asText();
        if (value == null || value.isBlank() || "undefined".equalsIgnoreCase(value) || "?".equals(value)) return null;
        return value;
    }

    private Integer integerAt(JsonNode root, String pointer) {
        JsonNode node = root == null ? null : root.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        return node.isNumber() ? node.intValue() : parseInteger(node.asText());
    }

    private Long longAt(JsonNode root, String pointer) {
        JsonNode node = root == null ? null : root.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        try { return node.isNumber() ? node.longValue() : Long.valueOf(node.asText()); }
        catch (NumberFormatException ex) { return null; }
    }

    private Integer parseInteger(String value) {
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ex) { return null; }
    }

    record ScoreBand(String label, String meaning) {}
}
