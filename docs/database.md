# Database

MySQL, accessed through Spring Data JPA/Hibernate. Schema is owned by [Flyway](https://flywaydb.org/) migrations in `backend/src/main/resources/db/migration` (`V1__init.sql` is the full baseline schema, matching the tables below exactly); `spring.jpa.hibernate.ddl-auto=validate` in every profile only checks the migrated schema against the `@Entity` classes and never changes it. This runs identically against local MySQL and Cloud SQL — a brand-new database gets its schema created automatically on first boot, and `baseline-on-migrate` (see `application.yml`) safely adopts a database that already has Hibernate-created tables from before this project used Flyway, without re-running `V1`.

## Tables

### `users`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(120) | |
| email | VARCHAR(190) | unique |
| phone | VARCHAR(32) | nullable |
| password_hash | VARCHAR | BCrypt |
| role | VARCHAR(20) | `USER` / `ADMIN` |
| fcm_token | VARCHAR(300) | nullable, latest FCM registration token |
| created_at, updated_at | TIMESTAMP | |

### `contacts`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| owner_user_id | BIGINT | the traveller who added this contact |
| name | VARCHAR(120) | |
| phone | VARCHAR(32) | nullable |
| email | VARCHAR(190) | nullable — matched against `users.email` to resolve watcher access, see [architecture.md](architecture.md) |
| relationship | VARCHAR(60) | nullable, free text |
| created_at, updated_at | TIMESTAMP | |

### `trips`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| traveller_user_id | BIGINT | |
| destination_name | VARCHAR(200) | |
| latitude, longitude | DOUBLE | |
| geofence_radius_m | INT | default 150 |
| expected_arrival | TIMESTAMP | |
| grace_period_minutes | INT | |
| status | VARCHAR(20) | `ACTIVE` / `COMPLETED` / `ESCALATED` / `CANCELLED` |
| nudge_sent_at | TIMESTAMP | nullable |
| started_at, completed_at, escalated_at | TIMESTAMP | nullable except started_at |
| created_at, updated_at | TIMESTAMP | |

### `watchers`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| trip_id | BIGINT | |
| contact_id | BIGINT | |
| permission | VARCHAR(30) | `ALERT_ONLY` / `LOCATION_DURING_TRIP` |
| created_at | TIMESTAMP | |
| | | unique on (trip_id, contact_id) |

### `locations`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| trip_id | BIGINT | |
| latitude, longitude | DOUBLE | |
| timestamp | TIMESTAMP | |

Deleted wholesale (`DELETE ... WHERE trip_id = ?`) when a trip completes or is cancelled — see the privacy rules in [architecture.md](architecture.md).

### `alerts`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| trip_id | BIGINT | nullable — SOS can be standalone (e.g. wearable press with no active trip) |
| traveller_user_id | BIGINT | |
| alert_type | VARCHAR(20) | `ESCALATION` / `SOS` |
| source | VARCHAR(20) | `APP` / `WEARABLE` / `SCHEDULER` |
| triggered_at | TIMESTAMP | |
| resolved | BOOLEAN | |
| resolved_at | TIMESTAMP | nullable |
| message | VARCHAR(500) | |
| latitude, longitude | DOUBLE | nullable |

### `wearable_devices`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT | |
| device_name | VARCHAR(100) | |
| ble_identifier | VARCHAR(64) | the ESP32's BLE MAC address as scanned by Android |
| pairing_state | VARCHAR(20) | `PAIRED` / `UNPAIRED` |
| last_seen | TIMESTAMP | nullable |
| created_at, updated_at | TIMESTAMP | |

## Limitations

Only one migration exists so far (`V1__init.sql`, the baseline schema below). Any future schema change should be added as a new versioned migration (`V2__...sql`, etc.) rather than editing `V1` or relying on `ddl-auto` again — Flyway rejects a checksum-mismatched edit to an already-applied migration.
