package com.pinghome.backend.trip;

import com.pinghome.backend.security.CurrentUser;
import com.pinghome.backend.trip.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.create(CurrentUser.id(), request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TripResponse>> mine() {
        return ResponseEntity.ok(tripService.listMine(CurrentUser.id()));
    }

    /** Trips where the caller's account is a registered watcher (any trip status). */
    @GetMapping("/watching")
    public ResponseEntity<List<TripResponse>> watching() {
        return ResponseEntity.ok(tripService.listWatching(CurrentUser.id()));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> get(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.get(CurrentUser.id(), tripId));
    }

    @PostMapping("/{tripId}/confirm-safe")
    public ResponseEntity<TripResponse> confirmSafe(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.confirmSafe(CurrentUser.id(), tripId));
    }

    /** Called by the Android app when the destination geofence transition fires. */
    @PostMapping("/{tripId}/complete-arrival")
    public ResponseEntity<TripResponse> completeArrival(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.completeArrival(CurrentUser.id(), tripId));
    }

    @PostMapping("/{tripId}/cancel")
    public ResponseEntity<TripResponse> cancel(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.cancel(CurrentUser.id(), tripId));
    }

    @PostMapping("/{tripId}/locations")
    public ResponseEntity<Void> addLocation(@PathVariable Long tripId, @Valid @RequestBody LocationUpdateRequest request) {
        tripService.addLocation(CurrentUser.id(), tripId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tripId}/locations/latest")
    public ResponseEntity<LocationResponse> latestLocation(@PathVariable Long tripId) {
        return tripService.latestLocation(CurrentUser.id(), tripId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
