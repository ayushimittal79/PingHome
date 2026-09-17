package com.pinghome.backend.trip;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "watchers", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "contact_id"}))
public class Watcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WatcherPermission permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public WatcherPermission getPermission() { return permission; }
    public void setPermission(WatcherPermission permission) { this.permission = permission; }
    public Instant getCreatedAt() { return createdAt; }
}
