package com.pinghome.backend.wearable;

import com.pinghome.backend.security.CurrentUser;
import com.pinghome.backend.wearable.dto.PairDeviceRequest;
import com.pinghome.backend.wearable.dto.WearableDeviceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wearables")
public class WearableController {

    private final WearableService wearableService;

    public WearableController(WearableService wearableService) {
        this.wearableService = wearableService;
    }

    @GetMapping
    public ResponseEntity<List<WearableDeviceResponse>> list() {
        return ResponseEntity.ok(wearableService.list(CurrentUser.id()).stream()
                .map(WearableDeviceResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<WearableDeviceResponse> pair(@Valid @RequestBody PairDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WearableDeviceResponse.from(wearableService.pair(CurrentUser.id(), request)));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> unpair(@PathVariable Long deviceId) {
        wearableService.unpair(CurrentUser.id(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
