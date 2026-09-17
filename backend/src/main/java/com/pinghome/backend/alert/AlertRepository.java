package com.pinghome.backend.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByTripIdOrderByTriggeredAtDesc(Long tripId);
    List<Alert> findByTravellerUserIdOrderByTriggeredAtDesc(Long travellerUserId);

    /** Used to dedupe rapid-fire SOS taps/wearable events within a short window. */
    Optional<Alert> findFirstByTravellerUserIdAndAlertTypeAndTriggeredAtAfterOrderByTriggeredAtDesc(
            Long travellerUserId, AlertType alertType, Instant after);
}
