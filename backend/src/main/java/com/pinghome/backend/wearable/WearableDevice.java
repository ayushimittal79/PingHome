package com.pinghome.backend.wearable;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wearable_devices")
public class WearableDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /** The ESP32's BLE MAC address / advertised identifier, as scanned by the Android app. */
    @Column(name = "ble_identifier", nullable = false, length = 64)
    private String bleIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "pairing_state", nullable = false, length = 20)
    private PairingState pairingState = PairingState.PAIRED;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getBleIdentifier() { return bleIdentifier; }
    public void setBleIdentifier(String bleIdentifier) { this.bleIdentifier = bleIdentifier; }
    public PairingState getPairingState() { return pairingState; }
    public void setPairingState(PairingState pairingState) { this.pairingState = pairingState; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
