package com.pinghome.backend.trip;

import com.pinghome.backend.alert.AlertSource;
import com.pinghome.backend.security.CurrentUser;
import com.pinghome.backend.trip.dto.SosRequest;
import com.pinghome.backend.wearable.WearableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOS is the single highest-priority action in PingHome. Both endpoints below end up in
 * TripService#triggerSos, which is deliberately independent of any particular trip: SOS must
 * work even if the traveller has no ACTIVE trip running (e.g. a wearable press outside of a
 * planned trip window).
 */
@RestController
@RequestMapping("/api/sos")
public class SosController {

    private final TripService tripService;
    private final WearableService wearableService;

    public SosController(TripService tripService, WearableService wearableService) {
        this.tripService = tripService;
        this.wearableService = wearableService;
    }

    /** Triggered by the in-app SOS button. */
    @PostMapping
    public ResponseEntity<Void> manualSos(@Valid @RequestBody SosRequest request) {
        tripService.triggerSos(CurrentUser.id(), request.tripId(), request.latitude(), request.longitude(), AlertSource.APP);
        return ResponseEntity.accepted().build();
    }

    /** Triggered by the Android app after it receives an SOS notification over BLE from the paired ESP32. */
    @PostMapping("/wearable")
    public ResponseEntity<Void> wearableSos(@Valid @RequestBody SosRequest request) {
        if (request.bleIdentifier() != null) {
            wearableService.touchLastSeen(CurrentUser.id(), request.bleIdentifier());
        }
        tripService.triggerSos(CurrentUser.id(), request.tripId(), request.latitude(), request.longitude(), AlertSource.WEARABLE);
        return ResponseEntity.accepted().build();
    }
}
