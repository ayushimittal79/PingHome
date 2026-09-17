package com.pinghome.backend.trip;

import com.pinghome.backend.alert.Alert;
import com.pinghome.backend.alert.AlertService;
import com.pinghome.backend.alert.AlertSource;
import com.pinghome.backend.alert.AlertType;
import com.pinghome.backend.common.exception.ConflictException;
import com.pinghome.backend.common.exception.ForbiddenException;
import com.pinghome.backend.common.exception.ResourceNotFoundException;
import com.pinghome.backend.contact.Contact;
import com.pinghome.backend.contact.ContactRepository;
import com.pinghome.backend.contact.ContactService;
import com.pinghome.backend.notification.NotificationDispatcher;
import com.pinghome.backend.trip.dto.*;
import com.pinghome.backend.user.User;
import com.pinghome.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final WatcherRepository watcherRepository;
    private final LocationPingRepository locationPingRepository;
    private final ContactRepository contactRepository;
    private final ContactService contactService;
    private final UserRepository userRepository;
    private final AlertService alertService;
    private final NotificationDispatcher notificationDispatcher;
    private final WatcherAccessService watcherAccessService;
    private final long sosDedupSeconds;

    public TripService(TripRepository tripRepository, WatcherRepository watcherRepository,
                        LocationPingRepository locationPingRepository, ContactRepository contactRepository,
                        ContactService contactService, UserRepository userRepository, AlertService alertService,
                        NotificationDispatcher notificationDispatcher, WatcherAccessService watcherAccessService,
                        @Value("${pinghome.sos.dedup-seconds:15}") long sosDedupSeconds) {
        this.tripRepository = tripRepository;
        this.watcherRepository = watcherRepository;
        this.locationPingRepository = locationPingRepository;
        this.contactRepository = contactRepository;
        this.contactService = contactService;
        this.userRepository = userRepository;
        this.alertService = alertService;
        this.notificationDispatcher = notificationDispatcher;
        this.watcherAccessService = watcherAccessService;
        this.sosDedupSeconds = sosDedupSeconds;
    }

    @Transactional
    public TripResponse create(Long travellerId, CreateTripRequest request) {
        Trip trip = new Trip();
        trip.setTravellerUserId(travellerId);
        trip.setDestinationName(request.destinationName());
        trip.setLatitude(request.latitude());
        trip.setLongitude(request.longitude());
        if (request.geofenceRadiusMeters() != null) trip.setGeofenceRadiusMeters(request.geofenceRadiusMeters());
        trip.setExpectedArrival(request.expectedArrival());
        trip.setGracePeriodMinutes(request.gracePeriodMinutes());
        trip.setStatus(TripStatus.ACTIVE);
        tripRepository.save(trip);

        for (WatcherAssignment assignment : request.watchers()) {
            // Ownership check - a traveller may only assign their own trusted contacts as watchers.
            contactService.getOwned(travellerId, assignment.contactId());

            Watcher watcher = new Watcher();
            watcher.setTripId(trip.getId());
            watcher.setContactId(assignment.contactId());
            watcher.setPermission(assignment.permission());
            watcherRepository.save(watcher);
        }

        return toResponse(trip);
    }

    public TripResponse get(Long requesterId, Long tripId) {
        Trip trip = findOwnedOrWatched(requesterId, tripId);
        return toResponse(trip);
    }

    public List<TripResponse> listMine(Long travellerId) {
        return tripRepository.findByTravellerUserIdOrderByCreatedAtDesc(travellerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TripResponse> listWatching(Long requesterId) {
        List<Long> tripIds = watcherAccessService.watchedTripIds(requesterId);
        return tripRepository.findAllById(tripIds).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Traveller taps "I'm Safe". Idempotent: calling this again on an already-terminal trip is a no-op. */
    @Transactional
    public TripResponse confirmSafe(Long travellerId, Long tripId) {
        Trip trip = findOwned(travellerId, tripId);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            completeTrip(trip);
        }
        return toResponse(trip);
    }

    /** Android geofence entry into the destination radius. Idempotent for the same reason as confirmSafe. */
    @Transactional
    public TripResponse completeArrival(Long travellerId, Long tripId) {
        Trip trip = findOwned(travellerId, tripId);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            completeTrip(trip);
        }
        return toResponse(trip);
    }

    @Transactional
    public TripResponse cancel(Long travellerId, Long tripId) {
        Trip trip = findOwned(travellerId, tripId);
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.setStatus(TripStatus.CANCELLED);
            trip.setCompletedAt(Instant.now());
            locationPingRepository.deleteByTripId(trip.getId());
        }
        return toResponse(trip);
    }

    @Transactional
    public void addLocation(Long travellerId, Long tripId, LocationUpdateRequest request) {
        Trip trip = findOwned(travellerId, tripId);
        if (trip.getStatus() != TripStatus.ACTIVE) {
            // Privacy rule: never accept/store location outside an ACTIVE trip window.
            throw new ConflictException("Trip is not active - location updates are not accepted");
        }
        LocationPing ping = new LocationPing();
        ping.setTripId(tripId);
        ping.setLatitude(request.latitude());
        ping.setLongitude(request.longitude());
        locationPingRepository.save(ping);
    }

    public Optional<LocationResponse> latestLocation(Long requesterId, Long tripId) {
        Trip trip = findOwnedOrWatched(requesterId, tripId);

        boolean statusAllowsLocation = trip.getStatus() == TripStatus.ACTIVE || trip.getStatus() == TripStatus.ESCALATED;
        if (!statusAllowsLocation) return Optional.empty();

        if (!watcherAccessService.isTraveller(requesterId, trip) && !watcherAccessService.canViewLocation(requesterId, trip)) {
            throw new ForbiddenException("You do not have permission to view this trip's location");
        }

        return locationPingRepository.findFirstByTripIdOrderByTimestampDesc(tripId).map(LocationResponse::from);
    }

    /** Manual SOS. May be tied to an active trip, or standalone (e.g. wearable press with no active trip). */
    @Transactional
    public void triggerSos(Long travellerId, Long tripId, Double latitude, Double longitude, AlertSource source) {
        if (alertService.hasRecentAlert(travellerId, AlertType.SOS, sosDedupSeconds)) {
            return; // Prevent duplicate submissions (double-tap, retried BLE event) from spamming watchers.
        }

        Trip trip = null;
        if (tripId != null) {
            trip = findOwned(travellerId, tripId);
            if (trip.getStatus() == TripStatus.ACTIVE) {
                trip.setStatus(TripStatus.ESCALATED);
                trip.setEscalatedAt(Instant.now());
            }
        }

        Alert alert = alertService.create(tripId, travellerId, AlertType.SOS, source,
                "Manual SOS triggered" + (source == AlertSource.WEARABLE ? " from wearable device" : ""),
                latitude, longitude);

        notifyWatchersOfAlert(trip, alert, latitude, longitude, true);
    }

    /** Called by EscalationScheduler once the nudge timeout elapses without confirmation. */
    @Transactional
    public void escalate(Trip trip, Double lastKnownLat, Double lastKnownLng) {
        trip.setStatus(TripStatus.ESCALATED);
        trip.setEscalatedAt(Instant.now());

        Alert alert = alertService.create(trip.getId(), trip.getTravellerUserId(), AlertType.ESCALATION,
                AlertSource.SCHEDULER, "Traveller did not confirm safety within the grace + nudge window",
                lastKnownLat, lastKnownLng);

        notifyWatchersOfAlert(trip, alert, lastKnownLat, lastKnownLng, true);
    }

    private void completeTrip(Trip trip) {
        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        // Privacy: stop sharing/monitoring immediately - purge the historical trail, only the
        // fact that the trip completed (and when) needs to persist.
        locationPingRepository.deleteByTripId(trip.getId());
    }

    private void notifyWatchersOfAlert(Trip trip, Alert alert, Double lat, Double lng, boolean critical) {
        if (trip == null) return;

        User traveller = userRepository.findById(trip.getTravellerUserId()).orElse(null);
        String travellerName = traveller != null ? traveller.getName() : "A PingHome user";

        for (Watcher watcher : watcherRepository.findByTripId(trip.getId())) {
            Contact contact = contactRepository.findById(watcher.getContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null) continue;

            User watcherUser = userRepository.findByEmailIgnoreCase(contact.getEmail()).orElse(null);
            if (watcherUser == null) continue; // Contact isn't a registered PingHome account - nothing to push to.

            String title = alert.getAlertType() == AlertType.SOS
                    ? travellerName + " triggered an SOS"
                    : travellerName + " missed their check-in";
            String body = "Destination: " + trip.getDestinationName();

            Map<String, String> data = watcher.getPermission() == WatcherPermission.LOCATION_DURING_TRIP && lat != null
                    ? Map.of("tripId", String.valueOf(trip.getId()), "alertId", String.valueOf(alert.getId()),
                             "latitude", String.valueOf(lat), "longitude", String.valueOf(lng))
                    : Map.of("tripId", String.valueOf(trip.getId()), "alertId", String.valueOf(alert.getId()));

            notificationDispatcher.notifyUser(watcherUser.getId(), title, body, data, critical);
        }
    }

    Trip findOwned(Long travellerId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (!trip.getTravellerUserId().equals(travellerId)) {
            throw new ForbiddenException("This trip does not belong to you");
        }
        return trip;
    }

    private Trip findOwnedOrWatched(Long requesterId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (!watcherAccessService.canView(requesterId, trip)) {
            throw new ForbiddenException("You do not have access to this trip");
        }
        return trip;
    }

    private TripResponse toResponse(Trip trip) {
        List<WatcherResponse> watchers = watcherRepository.findByTripId(trip.getId()).stream()
                .map(w -> {
                    Contact c = contactRepository.findById(w.getContactId()).orElse(null);
                    return new WatcherResponse(w.getContactId(), c != null ? c.getName() : "Unknown", w.getPermission());
                })
                .toList();
        return TripResponse.from(trip, watchers);
    }
}
