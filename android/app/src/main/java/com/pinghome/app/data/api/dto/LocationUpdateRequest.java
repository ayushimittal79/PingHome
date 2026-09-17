package com.pinghome.app.data.api.dto;

public class LocationUpdateRequest {
    public double latitude;
    public double longitude;

    public LocationUpdateRequest(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
