package com.pinghome.backend.alert;

import com.pinghome.backend.common.exception.ForbiddenException;
import com.pinghome.backend.common.exception.ResourceNotFoundException;
import com.pinghome.backend.trip.Trip;
import com.pinghome.backend.trip.TripRepository;
import com.pinghome.backend.trip.WatcherAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final TripRepository tripRepository;
    private final WatcherAccessService watcherAccessService;

    public AlertService(AlertRepository alertRepository, TripRepository tripRepository,
                         WatcherAccessService watcherAccessService) {
        this.alertRepository = alertRepository;
        this.tripRepository = tripRepository;
        this.watcherAccessService = watcherAccessService;
    }

    @Transactional
    public Alert create(Long tripId, Long travellerUserId, AlertType type, AlertSource source,
                         String message, Double latitude, Double longitude) {
        Alert alert = new Alert();
        alert.setTripId(tripId);
        alert.setTravellerUserId(travellerUserId);
        alert.setAlertType(type);
        alert.setSource(source);
        alert.setMessage(message);
        alert.setLatitude(latitude);
        alert.setLongitude(longitude);
        return alertRepository.save(alert);
    }

    public List<Alert> myAlerts(Long travellerUserId) {
        return alertRepository.findByTravellerUserIdOrderByTriggeredAtDesc(travellerUserId);
    }

    public List<Alert> alertsForTrip(Long requesterUserId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (!watcherAccessService.canView(requesterUserId, trip)) {
            throw new ForbiddenException("You do not have access to this trip's alerts");
        }
        return alertRepository.findByTripIdOrderByTriggeredAtDesc(tripId);
    }

    @Transactional
    public Alert resolve(Long requesterUserId, Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));

        boolean authorized = alert.getTravellerUserId().equals(requesterUserId);
        if (!authorized && alert.getTripId() != null) {
            Trip trip = tripRepository.findById(alert.getTripId()).orElse(null);
            authorized = trip != null && watcherAccessService.canView(requesterUserId, trip);
        }
        if (!authorized) {
            throw new ForbiddenException("You are not authorized to resolve this alert");
        }

        alert.setResolved(true);
        alert.setResolvedAt(Instant.now());
        return alert;
    }

    /** Dedup guard: has this traveller triggered an alert of this type in the last `withinSeconds`? */
    public boolean hasRecentAlert(Long travellerUserId, AlertType type, long withinSeconds) {
        return alertRepository
                .findFirstByTravellerUserIdAndAlertTypeAndTriggeredAtAfterOrderByTriggeredAtDesc(
                        travellerUserId, type, Instant.now().minusSeconds(withinSeconds))
                .isPresent();
    }
}
