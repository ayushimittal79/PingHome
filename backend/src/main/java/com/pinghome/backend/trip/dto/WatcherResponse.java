package com.pinghome.backend.trip.dto;

import com.pinghome.backend.trip.WatcherPermission;

public record WatcherResponse(Long contactId, String contactName, WatcherPermission permission) {}
