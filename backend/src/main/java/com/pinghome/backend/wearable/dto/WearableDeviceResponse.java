package com.pinghome.backend.wearable.dto;

import com.pinghome.backend.wearable.PairingState;
import com.pinghome.backend.wearable.WearableDevice;

import java.time.Instant;

public record WearableDeviceResponse(
        Long id, String deviceName, String bleIdentifier, PairingState pairingState, Instant lastSeen
) {
    public static WearableDeviceResponse from(WearableDevice device) {
        return new WearableDeviceResponse(device.getId(), device.getDeviceName(), device.getBleIdentifier(),
                device.getPairingState(), device.getLastSeen());
    }
}
