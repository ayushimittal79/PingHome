package com.pinghome.backend.trip.dto;

import com.pinghome.backend.trip.Trip;
import com.pinghome.backend.trip.TripStatus;

import java.time.Instant;
import java.util.List;

public record TripResponse(
        Long id,
        Long travellerUserId,
        String destinationName,
        Double latitude,
        Double longitude,
        Integer geofenceRadiusMeters,
        Instant expectedArrival,
        Integer gracePeriodMinutes,
        TripStatus status,
        Instant nudgeSentAt,
        Instant startedAt,
        Instant completedAt,
        Instant escalatedAt,
        List<WatcherResponse> watchers
) {
    public static TripResponse from(Trip trip, List<WatcherResponse> watchers) {
        return new TripResponse(
                trip.getId(), trip.getTravellerUserId(), trip.getDestinationName(),
                trip.getLatitude(), trip.getLongitude(), trip.getGeofenceRadiusMeters(),
                trip.getExpectedArrival(), trip.getGracePeriodMinutes(), trip.getStatus(),
                trip.getNudgeSentAt(), trip.getStartedAt(), trip.getCompletedAt(), trip.getEscalatedAt(),
                watchers
        );
    }
}
