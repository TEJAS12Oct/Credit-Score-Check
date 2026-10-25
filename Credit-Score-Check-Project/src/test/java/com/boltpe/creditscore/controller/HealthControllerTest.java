package com.boltpe.creditscore.controller;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void returnsServiceUp() {
        var response = new HealthController().health();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals("SERVICE_UP", response.getBody().code());
        assertEquals("UP", response.getBody().data().get("status"));
        assertNotNull(response.getBody().data().get("timestamp"));
    }
}
