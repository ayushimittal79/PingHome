-- Baseline schema for PingHome, matching exactly what Hibernate's ddl-auto=update generated
-- from the current JPA entities (verified against a real local MySQL 8 instance - see
-- docs/database.md). From here on, schema changes are versioned migrations (V2__..., V3__...),
-- not implicit ddl-auto changes, so the same schema-creation path runs identically against
-- local MySQL and a brand-new Cloud SQL instance. No foreign-key constraints are defined here,
-- matching the entities themselves (they store plain id columns, not JPA relationships) -
-- referential integrity across trip/contact/user/watcher ids is enforced in the service layer.

CREATE TABLE users (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6) NOT NULL,
    email         VARCHAR(190) NOT NULL,
    fcm_token     VARCHAR(300) DEFAULT NULL,
    name          VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(32) DEFAULT NULL,
    role          ENUM('ADMIN','USER') NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contacts (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6) NOT NULL,
    email         VARCHAR(190) DEFAULT NULL,
    name          VARCHAR(120) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    phone         VARCHAR(32) DEFAULT NULL,
    relationship  VARCHAR(60) DEFAULT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_contacts_owner_user_id ON contacts (owner_user_id);

CREATE TABLE trips (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    completed_at         DATETIME(6) DEFAULT NULL,
    created_at           DATETIME(6) NOT NULL,
    destination_name     VARCHAR(200) NOT NULL,
    escalated_at         DATETIME(6) DEFAULT NULL,
    expected_arrival     DATETIME(6) NOT NULL,
    geofence_radius_m    INT NOT NULL,
    grace_period_minutes INT NOT NULL,
    latitude             DOUBLE NOT NULL,
    longitude            DOUBLE NOT NULL,
    nudge_sent_at        DATETIME(6) DEFAULT NULL,
    started_at           DATETIME(6) NOT NULL,
    status               ENUM('ACTIVE','CANCELLED','COMPLETED','ESCALATED') NOT NULL,
    traveller_user_id    BIGINT NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_trips_traveller_user_id ON trips (traveller_user_id);
-- Supports EscalationScheduler's periodic poll for ACTIVE trips past their expected arrival.
CREATE INDEX idx_trips_status ON trips (status);

CREATE TABLE watchers (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    contact_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    permission ENUM('ALERT_ONLY','LOCATION_DURING_TRIP') NOT NULL,
    trip_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_watchers_trip_contact (trip_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_watchers_contact_id ON watchers (contact_id);

CREATE TABLE locations (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    latitude  DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    timestamp DATETIME(6) NOT NULL,
    trip_id   BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_locations_trip_id ON locations (trip_id);

CREATE TABLE alerts (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    alert_type        ENUM('ESCALATION','SOS') NOT NULL,
    latitude          DOUBLE DEFAULT NULL,
    longitude         DOUBLE DEFAULT NULL,
    message           VARCHAR(500) DEFAULT NULL,
    resolved          BIT(1) NOT NULL,
    resolved_at       DATETIME(6) DEFAULT NULL,
    source            ENUM('APP','SCHEDULER','WEARABLE') NOT NULL,
    traveller_user_id BIGINT NOT NULL,
    triggered_at      DATETIME(6) NOT NULL,
    trip_id           BIGINT DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_alerts_trip_id ON alerts (trip_id);
CREATE INDEX idx_alerts_traveller_user_id ON alerts (traveller_user_id);

CREATE TABLE wearable_devices (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    ble_identifier VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    device_name    VARCHAR(100) NOT NULL,
    last_seen      DATETIME(6) DEFAULT NULL,
    pairing_state  ENUM('PAIRED','UNPAIRED') NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    user_id        BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_wearable_devices_user_id ON wearable_devices (user_id);
