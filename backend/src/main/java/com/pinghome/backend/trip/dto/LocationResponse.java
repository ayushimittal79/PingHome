package com.pinghome.backend.trip.dto;

import com.pinghome.backend.trip.LocationPing;

import java.time.Instant;

public record LocationResponse(Double latitude, Double longitude, Instant timestamp) {
    public static LocationResponse from(LocationPing ping) {
        return new LocationResponse(ping.getLatitude(), ping.getLongitude(), ping.getTimestamp());
    }
}
