package com.pinghome.backend.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {
    Optional<LocationPing> findFirstByTripIdOrderByTimestampDesc(Long tripId);
    void deleteByTripId(Long tripId);
}
