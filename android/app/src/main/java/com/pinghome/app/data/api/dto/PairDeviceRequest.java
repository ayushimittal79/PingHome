package com.pinghome.app.data.api.dto;

public class PairDeviceRequest {
    public String deviceName;
    public String bleIdentifier;

    public PairDeviceRequest(String deviceName, String bleIdentifier) {
        this.deviceName = deviceName;
        this.bleIdentifier = bleIdentifier;
    }
}
