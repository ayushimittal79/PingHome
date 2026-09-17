package com.pinghome.backend.trip;

import com.pinghome.backend.contact.Contact;
import com.pinghome.backend.contact.ContactRepository;
import com.pinghome.backend.user.User;
import com.pinghome.backend.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves whether an authenticated user may view/act on a given trip, either as the
 * traveller who owns it or as a watcher. Watcher access is derived by matching the current
 * user's account email against the traveller's trusted-contact email that was assigned as a
 * watcher on the trip - contacts do not need a separate "invite accepted" step for this MVP,
 * but they DO need to correspond to a real registered PingHome account for that account to see
 * anything. This is the single place both TripService and AlertService call into, so ownership
 * and watcher-permission rules can never drift between the two.
 */
@Service
public class WatcherAccessService {

    private final ContactRepository contactRepository;
    private final WatcherRepository watcherRepository;
    private final UserRepository userRepository;

    public WatcherAccessService(ContactRepository contactRepository, WatcherRepository watcherRepository,
                                 UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.watcherRepository = watcherRepository;
        this.userRepository = userRepository;
    }

    public boolean isTraveller(Long userId, Trip trip) {
        return trip.getTravellerUserId().equals(userId);
    }

    /** Highest permission the given user holds as a watcher on this trip, if any. */
    public Optional<WatcherPermission> watcherPermissionFor(Long userId, Trip trip) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null) return Optional.empty();

        List<Contact> travellerContacts = contactRepository.findByOwnerUserIdOrderByNameAsc(trip.getTravellerUserId());
        List<Long> matchingContactIds = travellerContacts.stream()
                .filter(c -> user.getEmail().equalsIgnoreCase(c.getEmail()))
                .map(Contact::getId)
                .toList();

        if (matchingContactIds.isEmpty()) return Optional.empty();

        return watcherRepository.findByTripId(trip.getId()).stream()
                .filter(w -> matchingContactIds.contains(w.getContactId()))
                .map(Watcher::getPermission)
                .max((a, b) -> Integer.compare(rank(a), rank(b)));
    }

    public boolean canView(Long userId, Trip trip) {
        return isTraveller(userId, trip) || watcherPermissionFor(userId, trip).isPresent();
    }

    public boolean canViewLocation(Long userId, Trip trip) {
        if (isTraveller(userId, trip)) return true;
        return watcherPermissionFor(userId, trip)
                .map(p -> p == WatcherPermission.LOCATION_DURING_TRIP)
                .orElse(false);
    }

    /** All trips (any status) where the given user's account is a registered watcher. */
    public List<Long> watchedTripIds(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null) return List.of();

        List<Long> myContactIds = contactRepository.findAll().stream()
                .filter(c -> user.getEmail().equalsIgnoreCase(c.getEmail()))
                .map(Contact::getId)
                .toList();
        if (myContactIds.isEmpty()) return List.of();

        return watcherRepository.findByContactIdIn(myContactIds).stream()
                .map(Watcher::getTripId)
                .distinct()
                .toList();
    }

    private int rank(WatcherPermission permission) {
        return permission == WatcherPermission.LOCATION_DURING_TRIP ? 1 : 0;
    }
}
