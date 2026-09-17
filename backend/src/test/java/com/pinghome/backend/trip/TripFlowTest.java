package com.pinghome.backend.trip;

import com.pinghome.backend.contact.dto.ContactRequest;
import com.pinghome.backend.contact.dto.ContactResponse;
import com.pinghome.backend.trip.dto.*;
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
class TripFlowTest {

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

    private TripResponse createTrip(String travellerToken, Long watcherContactId) {
        CreateTripRequest request = new CreateTripRequest(
                "Home", 12.9716, 77.5946,
                Instant.now().plus(1, ChronoUnit.HOURS), 15, null,
                List.of(new WatcherAssignment(watcherContactId, WatcherPermission.LOCATION_DURING_TRIP))
        );
        var response = rest.exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(travellerToken)), TripResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Test
    void unauthorizedUserCannotAccessAnotherUsersTrip() {
        String travellerToken = registerAndLogin("Traveller One", "traveller1@example.com", "password-1");
        String watcherToken = registerAndLogin("Watcher One", "watcher1@example.com", "password-2");
        String strangerToken = registerAndLogin("Stranger", "stranger1@example.com", "password-3");

        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Watcher One", "+10000000002", "watcher1@example.com", "friend"),
                        authHeaders(travellerToken)),
                ContactResponse.class);
        Long contactId = contactResponse.getBody().id();

        TripResponse trip = createTrip(travellerToken, contactId);

        // Traveller can see their own trip.
        var ownerView = rest.exchange("/api/trips/" + trip.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(ownerView.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Registered watcher (matching contact email) can see it too.
        var watcherView = rest.exchange("/api/trips/" + trip.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(watcherToken)), TripResponse.class);
        assertThat(watcherView.getStatusCode()).isEqualTo(HttpStatus.OK);

        // An unrelated authenticated user must NOT be able to access it just by knowing the ID (IDOR).
        var strangerView = rest.exchange("/api/trips/" + trip.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(strangerToken)), String.class);
        assertThat(strangerView.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // A stranger must also not be able to act on it (e.g. cancel).
        var strangerCancel = rest.exchange("/api/trips/" + trip.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders(strangerToken)), String.class);
        assertThat(strangerCancel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void confirmSafeIsIdempotentAndStopsLocationSharingAfterCompletion() {
        String travellerToken = registerAndLogin("Traveller Two", "traveller2@example.com", "password-1");

        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Anyone", null, null, "friend"), authHeaders(travellerToken)),
                ContactResponse.class);
        // No watcher email on this contact - fine, trip creation just needs a valid owned contact id.
        Long contactId = contactResponse.getBody().id();

        TripResponse trip = createTrip(travellerToken, contactId);

        rest.exchange("/api/trips/" + trip.id() + "/locations", HttpMethod.POST,
                new HttpEntity<>(new LocationUpdateRequest(12.97, 77.59), authHeaders(travellerToken)), Void.class);

        // First confirm-safe call completes the trip.
        var first = rest.exchange("/api/trips/" + trip.id() + "/confirm-safe", HttpMethod.POST,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().status()).isEqualTo(TripStatus.COMPLETED);

        // Calling it again must not error and must not change the outcome (idempotent).
        var second = rest.exchange("/api/trips/" + trip.id() + "/confirm-safe", HttpMethod.POST,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().status()).isEqualTo(TripStatus.COMPLETED);

        // Privacy: location history must be gone after completion.
        var location = rest.exchange("/api/trips/" + trip.id() + "/locations/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaders(travellerToken)), String.class);
        assertThat(location.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And new location updates are rejected once the trip is no longer active.
        var rejected = rest.exchange("/api/trips/" + trip.id() + "/locations", HttpMethod.POST,
                new HttpEntity<>(new LocationUpdateRequest(1.0, 1.0), authHeaders(travellerToken)), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void arrivalGeofenceCompletionIsIdempotent() {
        String travellerToken = registerAndLogin("Traveller Three", "traveller3@example.com", "password-1");
        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Anyone", null, null, null), authHeaders(travellerToken)),
                ContactResponse.class);
        TripResponse trip = createTrip(travellerToken, contactResponse.getBody().id());

        var first = rest.exchange("/api/trips/" + trip.id() + "/complete-arrival", HttpMethod.POST,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(first.getBody().status()).isEqualTo(TripStatus.COMPLETED);

        // Duplicate geofence transition events must not error or re-escalate anything.
        var second = rest.exchange("/api/trips/" + trip.id() + "/complete-arrival", HttpMethod.POST,
                new HttpEntity<>(authHeaders(travellerToken)), TripResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().status()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void watcherWithoutLocationPermissionCannotSeeLocation() {
        String travellerToken = registerAndLogin("Traveller Four", "traveller4@example.com", "password-1");
        String watcherToken = registerAndLogin("Watcher Four", "watcher4@example.com", "password-2");

        var contactResponse = rest.exchange("/api/contacts", HttpMethod.POST,
                new HttpEntity<>(new ContactRequest("Watcher Four", null, "watcher4@example.com", "friend"),
                        authHeaders(travellerToken)),
                ContactResponse.class);
        Long contactId = contactResponse.getBody().id();

        CreateTripRequest request = new CreateTripRequest(
                "Home", 12.9716, 77.5946, Instant.now().plus(1, ChronoUnit.HOURS), 15, null,
                List.of(new WatcherAssignment(contactId, WatcherPermission.ALERT_ONLY))
        );
        var tripResponse = rest.exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(travellerToken)), TripResponse.class);
        Long tripId = tripResponse.getBody().id();

        rest.exchange("/api/trips/" + tripId + "/locations", HttpMethod.POST,
                new HttpEntity<>(new LocationUpdateRequest(12.97, 77.59), authHeaders(travellerToken)), Void.class);

        // ALERT_ONLY watcher can view the trip...
        var view = rest.exchange("/api/trips/" + tripId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(watcherToken)), TripResponse.class);
        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ...but must not be able to read live location.
        var location = rest.exchange("/api/trips/" + tripId + "/locations/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaders(watcherToken)), String.class);
        assertThat(location.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
