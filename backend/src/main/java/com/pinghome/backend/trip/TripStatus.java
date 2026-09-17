package com.pinghome.backend.trip;

public enum TripStatus {
    ACTIVE,
    COMPLETED,
    ESCALATED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
