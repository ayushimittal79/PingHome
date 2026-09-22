package com.pinghome.app.data.geo;

/** A geocoded place: what the user typed resolved to a real point on the map. */
public class Place {
    public final String displayName;
    public final String shortName;
    public final double latitude;
    public final double longitude;

    public Place(String displayName, String shortName, double latitude, double longitude) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
