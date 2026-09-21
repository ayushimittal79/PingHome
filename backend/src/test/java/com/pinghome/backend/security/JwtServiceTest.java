package com.pinghome.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String STRONG_SECRET = "unit-test-secret-that-is-comfortably-longer-than-32-bytes";

    @Test
    void deployedProfileRefusesTheCheckedInDevelopmentSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService(JwtService.LOCAL_DEV_DEFAULT_SECRET, 60, "pinghome-backend", true));
        assertTrue(ex.getMessage().contains("PINGHOME_JWT_SECRET"));
    }

    @Test
    void localProfileStillAcceptsTheDevelopmentSecret() {
        JwtService service = new JwtService(JwtService.LOCAL_DEV_DEFAULT_SECRET, 60, "pinghome-backend", false);
        String token = service.generateToken(42L, "dev@example.com");
        assertEquals(42L, service.extractUserId(service.parseClaims(token).orElseThrow()));
    }

    @Test
    void deployedProfileAcceptsARealSecret() {
        JwtService service = new JwtService(STRONG_SECRET, 60, "pinghome-backend", true);
        String token = service.generateToken(7L, "user@example.com");
        assertEquals("user@example.com", service.extractEmail(service.parseClaims(token).orElseThrow()));
    }
}
