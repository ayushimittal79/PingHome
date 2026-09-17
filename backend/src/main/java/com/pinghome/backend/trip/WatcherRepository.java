package com.pinghome.backend.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatcherRepository extends JpaRepository<Watcher, Long> {
    List<Watcher> findByTripId(Long tripId);
    List<Watcher> findByContactIdIn(List<Long> contactIds);
    Optional<Watcher> findByTripIdAndContactId(Long tripId, Long contactId);
}
