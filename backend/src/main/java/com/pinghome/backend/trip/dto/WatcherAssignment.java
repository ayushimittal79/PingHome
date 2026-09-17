package com.pinghome.backend.trip.dto;

import com.pinghome.backend.trip.WatcherPermission;
import jakarta.validation.constraints.NotNull;

public record WatcherAssignment(
        @NotNull Long contactId,
        @NotNull WatcherPermission permission
) {}
