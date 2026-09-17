package com.pinghome.app.data.api.dto;

public class SosRequest {
    public Long tripId;
    public Double latitude;
    public Double longitude;
    public String bleIdentifier;

    public SosRequest(Long tripId, Double latitude, Double longitude, String bleIdentifier) {
        this.tripId = tripId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.bleIdentifier = bleIdentifier;
    }
}
