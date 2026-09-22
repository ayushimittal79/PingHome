package com.pinghome.app.data.geo;

public class RouteEstimate {
    public enum Mode { WALKING, DRIVING }

    public final Mode mode;
    public final int durationSeconds;
    public final int distanceMeters;

    public RouteEstimate(Mode mode, int durationSeconds, int distanceMeters) {
        this.mode = mode;
        this.durationSeconds = durationSeconds;
        this.distanceMeters = distanceMeters;
    }
}
