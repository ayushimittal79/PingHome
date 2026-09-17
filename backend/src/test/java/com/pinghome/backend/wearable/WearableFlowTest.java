package com.pinghome.backend.wearable;

import com.pinghome.backend.trip.dto.SosRequest;
import com.pinghome.backend.wearable.dto.PairDeviceRequest;
import com.pinghome.backend.wearable.dto.WearableDeviceResponse;
import com.pinghome.backend.user.dto.AuthResponse;
import com.pinghome.backend.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers pairing an ESP32 device and relaying a wearable-triggered SOS through the Android
 * app's authenticated session, matching the physical button -> BLE -> Android -> backend flow
 * mandated in SPEC.md (the ESP32 firmware and BLE transport themselves are exercised on real
 * hardware, not here - see docs/testing.md). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WearableFlowTest {

    @Autowired
    private TestRestTemplate rest;

    private String registerAndLogin(String name, String email, String password) {
        var response = rest.postForEntity("/api/auth/register",
                new RegisterRequest(name, email, null, password), AuthResponse.class);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void pairDeviceThenRelayWearableSos() {
        String token = registerAndLogin("Wearable Owner", "wearable1@example.com", "password-1");

        var pairResponse = rest.exchange("/api/wearables", HttpMethod.POST,
                new HttpEntity<>(new PairDeviceRequest("PingHome Keychain", "AA:BB:CC:DD:EE:01"), authHeaders(token)),
                WearableDeviceResponse.class);
        assertThat(pairResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pairResponse.getBody().pairingState()).isEqualTo(PairingState.PAIRED);

        var sosResponse = rest.exchange("/api/sos/wearable", HttpMethod.POST,
                new HttpEntity<>(new SosRequest(null, 12.9, 77.5, "AA:BB:CC:DD:EE:01"), authHeaders(token)), Void.class);
        assertThat(sosResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        var devices = rest.exchange("/api/wearables", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), WearableDeviceResponse[].class);
        assertThat(devices.getBody()[0].lastSeen()).isNotNull();
    }

    @Test
    void cannotUnpairAnotherUsersDevice() {
        String ownerToken = registerAndLogin("Device Owner", "device-owner@example.com", "password-1");
        String otherToken = registerAndLogin("Someone Else", "someone-else@example.com", "password-2");

        var pairResponse = rest.exchange("/api/wearables", HttpMethod.POST,
                new HttpEntity<>(new PairDeviceRequest("PingHome Keychain", "AA:BB:CC:DD:EE:02"), authHeaders(ownerToken)),
                WearableDeviceResponse.class);
        Long deviceId = pairResponse.getBody().id();

        var deleteAttempt = rest.exchange("/api/wearables/" + deviceId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(otherToken)), String.class);
        assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
