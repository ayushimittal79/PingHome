package com.pinghome.backend.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record SosRequest(
        Long tripId,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        /** Only set when this SOS was relayed from a paired ESP32 wearable's BLE characteristic. */
        String bleIdentifier
) {}
