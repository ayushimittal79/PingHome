package com.pinghome.backend.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public record CreateTripRequest(
        @NotBlank @Size(max = 200) String destinationName,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Future Instant expectedArrival,
        @NotNull @Min(1) @Max(240) Integer gracePeriodMinutes,
        @Min(50) @Max(1000) Integer geofenceRadiusMeters,
        @NotEmpty @Valid List<WatcherAssignment> watchers
) {}
