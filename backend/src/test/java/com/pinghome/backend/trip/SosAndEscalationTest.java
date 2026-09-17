package com.pinghome.backend.trip;

import com.pinghome.backend.alert.AlertRepository;
import com.pinghome.backend.alert.AlertType;
import com.pinghome.backend.contact.dto.ContactRequest;
import com.pinghome.backend.contact.dto.ContactResponse;
import com.pinghome.backend.escalation.EscalationScheduler;
import com.pinghome.backend.trip.dto.CreateTripRequest;
import com.pinghome.backend.trip.dto.SosRequest;
import com.pinghome.backend.trip.dto.TripResponse;
import com.pinghome.backend.trip.dto.WatcherAssignment;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SosAndEscalationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private EscalationScheduler escalationScheduler;

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

    private TripResponse createTrip(String travellerToken, Long contactId) {
        CreateTripRequest request = new CreateTripRequest(
                "Home", 12.9716, 77.5946, Instant.now().plus(1, ChronoUnit.HOURS), 15, null,
                List.of(new WatcherAssignment(contactId, WatcherPermission.LOCATION_DURING_TRIP))
        );
        var response = rest.exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(travellerToken)), TripResponse.class);
        return response.getBody();
    }

    @Test
    void manualSosEscalatesTripAndCreatesAlertAndDeduplicatesRepeatedTaps() {
        String travellerToken = registerAndLogin("Traveller Sos", "sos1@example.com", "password-1");
        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Anyone", null, null, null), authHeaders(travellerToken)),
                ContactResponse.class);
        TripResponse trip = createTrip(travellerToken, contactResponse.getBody().id());

        SosRequest sosRequest = new SosRequest(trip.id(), 12.97, 77.59, null);
        var first = rest.exchange("/api/sos", HttpMethod.POST,
                new HttpEntity<>(sosRequest, authHeaders(travellerToken)), Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        var updated = rest.exchange("/api/trips/" + trip.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(updated.getBody().status()).isEqualTo(TripStatus.ESCALATED);

        long alertCountAfterFirst = alertRepository.findByTripIdOrderByTriggeredAtDesc(trip.id()).size();
        assertThat(alertCountAfterFirst).isEqualTo(1);

        // Duplicate/rapid repeated SOS taps (or a retried BLE event) must not create a second alert.
        rest.exchange("/api/sos", HttpMethod.POST,
                new HttpEntity<>(sosRequest, authHeaders(travellerToken)), Void.class);
        long alertCountAfterSecond = alertRepository.findByTripIdOrderByTriggeredAtDesc(trip.id()).size();
        assertThat(alertCountAfterSecond).isEqualTo(1);
    }

    @Test
    void missedArrivalNudgesThenEscalatesViaScheduler() {
        String travellerToken = registerAndLogin("Traveller Sched", "sched1@example.com", "password-1");
        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Anyone", null, null, null), authHeaders(travellerToken)),
                ContactResponse.class);
        TripResponse tripResponse = createTrip(travellerToken, contactResponse.getBody().id());

        // Force this trip's expected-arrival + grace period into the past to make it a nudge candidate.
        Trip trip = tripRepository.findById(tripResponse.id()).orElseThrow();
        trip.setExpectedArrival(Instant.now().minusSeconds(120));
        trip.setGracePeriodMinutes(0);
        tripRepository.save(trip);

        escalationScheduler.tick();

        Trip afterNudge = tripRepository.findById(tripResponse.id()).orElseThrow();
        assertThat(afterNudge.getStatus()).isEqualTo(TripStatus.ACTIVE);
        assertThat(afterNudge.getNudgeSentAt()).isNotNull();

        // Fast-forward the nudge timestamp beyond the nudge timeout window.
        afterNudge.setNudgeSentAt(Instant.now().minusSeconds(600));
        tripRepository.save(afterNudge);

        escalationScheduler.tick();

        Trip afterEscalation = tripRepository.findById(tripResponse.id()).orElseThrow();
        assertThat(afterEscalation.getStatus()).isEqualTo(TripStatus.ESCALATED);
        assertThat(afterEscalation.getEscalatedAt()).isNotNull();

        boolean hasEscalationAlert = alertRepository.findByTripIdOrderByTriggeredAtDesc(tripResponse.id()).stream()
                .anyMatch(a -> a.getAlertType() == AlertType.ESCALATION);
        assertThat(hasEscalationAlert).isTrue();
    }

    @Test
    void sosWorksWithoutAnActiveTrip() {
        String travellerToken = registerAndLogin("Traveller Standalone", "standalone1@example.com", "password-1");

        SosRequest sosRequest = new SosRequest(null, 12.97, 77.59, null);
        var response = rest.exchange("/api/sos", HttpMethod.POST,
                new HttpEntity<>(sosRequest, authHeaders(travellerToken)), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
