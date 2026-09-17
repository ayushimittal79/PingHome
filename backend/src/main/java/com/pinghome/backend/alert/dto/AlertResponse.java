package com.pinghome.backend.alert.dto;

import com.pinghome.backend.alert.Alert;
import com.pinghome.backend.alert.AlertSource;
import com.pinghome.backend.alert.AlertType;

import java.time.Instant;

public record AlertResponse(
        Long id,
        Long tripId,
        AlertType alertType,
        AlertSource source,
        Instant triggeredAt,
        boolean resolved,
        Instant resolvedAt,
        String message,
        Double latitude,
        Double longitude
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getTripId(), alert.getAlertType(), alert.getSource(),
                alert.getTriggeredAt(), alert.isResolved(), alert.getResolvedAt(), alert.getMessage(),
                alert.getLatitude(), alert.getLongitude());
    }
}
