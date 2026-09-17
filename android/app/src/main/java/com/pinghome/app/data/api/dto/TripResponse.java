package com.pinghome.app.data.api.dto;

import java.time.Instant;
import java.util.List;

public class TripResponse {
    public long id;
    public long travellerUserId;
    public String destinationName;
    public double latitude;
    public double longitude;
    public int geofenceRadiusMeters;
    public Instant expectedArrival;
    public int gracePeriodMinutes;
    public TripStatus status;
    public Instant nudgeSentAt;
    public Instant startedAt;
    public Instant completedAt;
    public Instant escalatedAt;
    public List<WatcherResponse> watchers;
}
