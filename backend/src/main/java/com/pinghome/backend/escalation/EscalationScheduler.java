package com.pinghome.backend.escalation;

import com.pinghome.backend.notification.NotificationDispatcher;
import com.pinghome.backend.trip.LocationPingRepository;
import com.pinghome.backend.trip.Trip;
import com.pinghome.backend.trip.TripRepository;
import com.pinghome.backend.trip.TripService;
import com.pinghome.backend.trip.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Owns the authoritative safety-escalation state machine described in SPEC.md:
 * ACTIVE -> (expected arrival + grace period elapses) -> nudge sent -> either the traveller
 * confirms (TripService#confirmSafe) or the nudge timeout elapses -> ESCALATED.
 *
 * This runs on the backend regardless of whether the Android app is foregrounded, backgrounded,
 * or closed - escalation must never depend on the app being open. The poll interval is
 * configurable (pinghome.scheduler.interval-ms) so demo mode can shorten it dramatically without
 * changing this logic at all.
 */
@Component
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    private final TripRepository tripRepository;
    private final TripService tripService;
    private final LocationPingRepository locationPingRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final long nudgeTimeoutMinutes;

    public EscalationScheduler(TripRepository tripRepository, TripService tripService,
                                LocationPingRepository locationPingRepository, NotificationDispatcher notificationDispatcher,
                                @Value("${pinghome.escalation.nudge-timeout-minutes:5}") long nudgeTimeoutMinutes) {
        this.tripRepository = tripRepository;
        this.tripService = tripService;
        this.locationPingRepository = locationPingRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.nudgeTimeoutMinutes = nudgeTimeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${pinghome.scheduler.interval-ms:30000}")
    @Transactional
    public void tick() {
        sendDueNudges();
        escalateTimedOutTrips();
    }

    private void sendDueNudges() {
        Instant now = Instant.now();
        List<Trip> candidates = tripRepository.findByStatusAndNudgeSentAtIsNull(TripStatus.ACTIVE);

        for (Trip trip : candidates) {
            Instant dueAt = trip.getExpectedArrival().plusSeconds(trip.getGracePeriodMinutes() * 60L);
            if (now.isBefore(dueAt)) continue;

            trip.setNudgeSentAt(now);
            notificationDispatcher.notifyUser(
                    trip.getTravellerUserId(),
                    "Still safe?",
                    "Tap to confirm you're OK for your trip to " + trip.getDestinationName() + ".",
                    Map.of("type", "NUDGE", "tripId", String.valueOf(trip.getId())),
                    false
            );
            log.info("Sent safety nudge for trip {}", trip.getId());
        }
    }

    private void escalateTimedOutTrips() {
        Instant now = Instant.now();
        List<Trip> candidates = tripRepository.findByStatusAndNudgeSentAtIsNotNull(TripStatus.ACTIVE);

        for (Trip trip : candidates) {
            Instant timeoutAt = trip.getNudgeSentAt().plusSeconds(nudgeTimeoutMinutes * 60L);
            if (now.isBefore(timeoutAt)) continue;

            var lastPing = locationPingRepository.findFirstByTripIdOrderByTimestampDesc(trip.getId());
            Double lat = lastPing.map(p -> p.getLatitude()).orElse(trip.getLatitude());
            Double lng = lastPing.map(p -> p.getLongitude()).orElse(trip.getLongitude());

            tripService.escalate(trip, lat, lng);
            log.warn("Escalated trip {} - no safety confirmation within nudge timeout", trip.getId());
        }
    }
}
