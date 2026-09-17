package com.pinghome.backend.trip;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "traveller_user_id", nullable = false)
    private Long travellerUserId;

    @Column(name = "destination_name", nullable = false, length = 200)
    private String destinationName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Radius of the arrival geofence the Android app registers for this trip. */
    @Column(name = "geofence_radius_m", nullable = false)
    private Integer geofenceRadiusMeters = 150;

    @Column(name = "expected_arrival", nullable = false)
    private Instant expectedArrival;

    @Column(name = "grace_period_minutes", nullable = false)
    private Integer gracePeriodMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.ACTIVE;

    /** Set once the "still safe?" nudge has been sent; null means no nudge sent yet. */
    @Column(name = "nudge_sent_at")
    private Instant nudgeSentAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (startedAt == null) startedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTravellerUserId() { return travellerUserId; }
    public void setTravellerUserId(Long travellerUserId) { this.travellerUserId = travellerUserId; }
    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getGeofenceRadiusMeters() { return geofenceRadiusMeters; }
    public void setGeofenceRadiusMeters(Integer geofenceRadiusMeters) { this.geofenceRadiusMeters = geofenceRadiusMeters; }
    public Instant getExpectedArrival() { return expectedArrival; }
    public void setExpectedArrival(Instant expectedArrival) { this.expectedArrival = expectedArrival; }
    public Integer getGracePeriodMinutes() { return gracePeriodMinutes; }
    public void setGracePeriodMinutes(Integer gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }
    public Instant getNudgeSentAt() { return nudgeSentAt; }
    public void setNudgeSentAt(Instant nudgeSentAt) { this.nudgeSentAt = nudgeSentAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
