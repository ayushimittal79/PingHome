package com.pinghome.app.data.api.dto;

import java.time.Instant;
import java.util.List;

public class CreateTripRequest {
    public String destinationName;
    public double latitude;
    public double longitude;
    public Instant expectedArrival;
    public int gracePeriodMinutes;
    public Integer geofenceRadiusMeters;
    public List<WatcherAssignment> watchers;

    public CreateTripRequest(String destinationName, double latitude, double longitude,
                              Instant expectedArrival, int gracePeriodMinutes,
                              Integer geofenceRadiusMeters, List<WatcherAssignment> watchers) {
        this.destinationName = destinationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.expectedArrival = expectedArrival;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.geofenceRadiusMeters = geofenceRadiusMeters;
        this.watchers = watchers;
    }
}
