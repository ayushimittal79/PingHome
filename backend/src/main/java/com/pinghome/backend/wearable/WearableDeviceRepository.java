package com.pinghome.backend.wearable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WearableDeviceRepository extends JpaRepository<WearableDevice, Long> {
    List<WearableDevice> findByUserId(Long userId);
    Optional<WearableDevice> findByUserIdAndBleIdentifier(Long userId, String bleIdentifier);
}
