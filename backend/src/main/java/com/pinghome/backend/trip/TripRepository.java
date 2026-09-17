package com.pinghome.backend.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByTravellerUserIdOrderByCreatedAtDesc(Long travellerUserId);

    List<Trip> findByStatus(TripStatus status);

    /** Candidates for a nudge: still active, no nudge sent yet. Grace period is per-trip, so the
     * expected-arrival + grace comparison happens in EscalationScheduler, not in this query. */
    List<Trip> findByStatusAndNudgeSentAtIsNull(TripStatus status);

    /** Candidates for escalation: nudge already sent, waiting on the nudge-timeout window. */
    List<Trip> findByStatusAndNudgeSentAtIsNotNull(TripStatus status);
}
