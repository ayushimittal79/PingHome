package com.pinghome.app.data.api.dto;

import java.time.Instant;

public class WearableDeviceResponse {
    public long id;
    public String deviceName;
    public String bleIdentifier;
    public String pairingState;
    public Instant lastSeen;
}
