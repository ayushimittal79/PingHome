package com.pinghome.backend.wearable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairDeviceRequest(
        @NotBlank @Size(max = 100) String deviceName,
        @NotBlank @Size(max = 64) String bleIdentifier
) {}
