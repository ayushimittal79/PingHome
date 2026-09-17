package com.pinghome.backend.alert;

import com.pinghome.backend.alert.dto.AlertResponse;
import com.pinghome.backend.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** Alerts triggered on trips the caller travelled on. */
    @GetMapping("/mine")
    public ResponseEntity<List<AlertResponse>> mine() {
        return ResponseEntity.ok(alertService.myAlerts(CurrentUser.id())
                .stream().map(AlertResponse::from).toList());
    }

    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<AlertResponse>> forTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(alertService.alertsForTrip(CurrentUser.id(), tripId)
                .stream().map(AlertResponse::from).toList());
    }

    @PatchMapping("/{alertId}/resolve")
    public ResponseEntity<AlertResponse> resolve(@PathVariable Long alertId) {
        return ResponseEntity.ok(AlertResponse.from(alertService.resolve(CurrentUser.id(), alertId)));
    }
}
