package com.boltpe.creditscore.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskingUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveDataMaskingUtil maskingUtil = new SensitiveDataMaskingUtil(objectMapper);

    @Test
    void masksSensitiveValues() throws Exception {

        Map<String, Object> input = Map.of(
                "panNumber", "ABCDE1234F",
                "mobileNumber", "9876543210",
                "email", "abc@test.com",
                "otp", "123456",
                "secretKey", "secret"
        );

        String sanitized = maskingUtil.sanitize(input);

        JsonNode json = objectMapper.readTree(sanitized);

        assertEquals("******234F", json.get("panNumber").asText());
        assertFalse(sanitized.contains("ABCDE1234F"));

        assertEquals("******3210", json.get("mobileNumber").asText());
        assertFalse(sanitized.contains("9876543210"));

        assertEquals("a***@test.com", json.get("email").asText());
        assertFalse(sanitized.contains("abc@test.com"));

        assertEquals("***", json.get("otp").asText());
        assertFalse(sanitized.contains("123456"));

        assertEquals("***", json.get("secretKey").asText());
        assertNotEquals("secret", json.get("secretKey").asText());

        assertTrue(sanitized.contains("234F"));
    }

    @Test
    void masksClientIdAndToken() throws Exception {

        Map<String, Object> input = Map.of(
                "clientId", "client-12345",
                "token", "vendor-token-12345"
        );

        String sanitized = maskingUtil.sanitize(input);

        JsonNode json = objectMapper.readTree(sanitized);

        assertEquals("***", json.get("clientId").asText());
        assertEquals("***", json.get("token").asText());

        assertFalse(sanitized.contains("client-12345"));
        assertFalse(sanitized.contains("vendor-token-12345"));
    }

    @Test
    void handlesNestedSensitiveValues() throws Exception {

        Map<String, Object> input = Map.of(
                "request",
                Map.of(
                        "panNumber", "ABCDE1234F",
                        "mobileNumber", "9876543210",
                        "otp", "123456"
                )
        );

        String sanitized = maskingUtil.sanitize(input);

        JsonNode json = objectMapper.readTree(sanitized);
        JsonNode request = json.get("request");

        assertEquals("******234F", request.get("panNumber").asText());
        assertEquals("******3210", request.get("mobileNumber").asText());
        assertEquals("***", request.get("otp").asText());

        assertFalse(sanitized.contains("ABCDE1234F"));
        assertFalse(sanitized.contains("9876543210"));
        assertFalse(sanitized.contains("123456"));
    }

    @Test
    void masksVendorAliasFields() throws Exception {
        Map<String, Object> input = Map.of(
                "Income_TAX_PAN", "ABCDE1234F",
                "Telephone_Number", "9876543210",
                "EMailId", "abc@test.com",
                "Account_Number", "123456789012");
        String sanitized = maskingUtil.sanitize(input);
        assertFalse(sanitized.contains("ABCDE1234F"));
        assertFalse(sanitized.contains("9876543210"));
        assertFalse(sanitized.contains("abc@test.com"));
        assertFalse(sanitized.contains("123456789012"));
        JsonNode json = objectMapper.readTree(sanitized);
        assertTrue(json.get("Income_TAX_PAN").asText().endsWith("234F"));
        assertTrue(json.get("Account_Number").asText().endsWith("9012"));
    }

    @Test
    void masksSensitiveFieldsInsideArraysAndKeepsNonSensitiveValues() throws Exception {
        Map<String, Object> input = Map.of("items", java.util.List.of(
                Map.of("accountNo", "1234567890", "access_token", "secret", "status", "ACTIVE"),
                Map.of("phone", "1234", "email", "invalid-email", "authorization", "Bearer secret")));
        JsonNode items = objectMapper.readTree(maskingUtil.sanitize(input)).get("items");
        assertEquals("******7890", items.get(0).get("accountNo").asText());
        assertEquals("***", items.get(0).get("access_token").asText());
        assertEquals("ACTIVE", items.get(0).get("status").asText());
        assertEquals("****", items.get(1).get("phone").asText());
        assertEquals("***", items.get(1).get("email").asText());
        assertEquals("***", items.get(1).get("authorization").asText());
    }

    @Test
    void handlesNullBlankAndShortSensitiveValues() throws Exception {
        assertEquals("null", maskingUtil.sanitize(null));
        JsonNode result = objectMapper.readTree(maskingUtil.sanitize(Map.of(
                "mobileNumber", " ", "pan", "ABC", "accountNum", "1234", "emailAddress", "a@b.co")));
        assertEquals(" ", result.get("mobileNumber").asText());
        assertEquals("****", result.get("pan").asText());
        assertEquals("****", result.get("accountNum").asText());
        assertEquals("a***@b.co", result.get("emailAddress").asText());
    }
    @Test
    void sanitizesFreeTextIdentifiersAndCredentials() {
        String input = "PAN ABCDE1234F email user@example.com mobile 9876543210 token=abc123";
        String result = maskingUtil.sanitizeFreeText(input);
        assertFalse(result.contains("ABCDE1234F"));
        assertFalse(result.contains("user@example.com"));
        assertFalse(result.contains("9876543210"));
        assertFalse(result.contains("abc123"));
        assertTrue(result.contains("[REDACTED_PAN]"));
        assertTrue(result.contains("[REDACTED_EMAIL]"));
        assertTrue(result.contains("[REDACTED_MOBILE]"));
    }

    @Test
    void freeTextNullAndLengthLimitAreHandled() {
        assertNull(maskingUtil.sanitizeFreeText(null));
        assertEquals(500, maskingUtil.sanitizeFreeText("x".repeat(700)).length());
        assertEquals("Safe text", maskingUtil.sanitizeFreeText("Safe text"));
    }

}
