package com.pinghome.backend.trip;

/**
 * ALERT_ONLY watchers are notified when a trip escalates but never see live coordinates.
 * LOCATION_DURING_TRIP watchers may additionally see the traveller's latest location while
 * the trip is ACTIVE or ESCALATED. Neither permission grants access outside those windows -
 * see TripService for the enforcement, not just this enum.
 */
public enum WatcherPermission {
    ALERT_ONLY,
    LOCATION_DURING_TRIP
}
