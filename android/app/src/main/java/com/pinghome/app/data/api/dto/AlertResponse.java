package com.pinghome.app.data.api.dto;

import java.time.Instant;

public class AlertResponse {
    public long id;
    public Long tripId;
    public String alertType;
    public String source;
    public Instant triggeredAt;
    public boolean resolved;
    public Instant resolvedAt;
    public String message;
    public Double latitude;
    public Double longitude;
}
