package com.pinghome.backend.wearable;

import com.pinghome.backend.common.exception.ForbiddenException;
import com.pinghome.backend.common.exception.ResourceNotFoundException;
import com.pinghome.backend.wearable.dto.PairDeviceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WearableService {

    private final WearableDeviceRepository repository;

    public WearableService(WearableDeviceRepository repository) {
        this.repository = repository;
    }

    public List<WearableDevice> list(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public WearableDevice pair(Long userId, PairDeviceRequest request) {
        WearableDevice device = repository.findByUserIdAndBleIdentifier(userId, request.bleIdentifier())
                .orElseGet(WearableDevice::new);
        device.setUserId(userId);
        device.setDeviceName(request.deviceName());
        device.setBleIdentifier(request.bleIdentifier());
        device.setPairingState(PairingState.PAIRED);
        device.setLastSeen(Instant.now());
        return repository.save(device);
    }

    @Transactional
    public void unpair(Long userId, Long deviceId) {
        WearableDevice device = getOwned(userId, deviceId);
        repository.delete(device);
    }

    @Transactional
    public void touchLastSeen(Long userId, String bleIdentifier) {
        repository.findByUserIdAndBleIdentifier(userId, bleIdentifier)
                .ifPresent(d -> d.setLastSeen(Instant.now()));
    }

    private WearableDevice getOwned(Long userId, Long deviceId) {
        WearableDevice device = repository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Wearable device not found"));
        if (!device.getUserId().equals(userId)) {
            throw new ForbiddenException("This device does not belong to you");
        }
        return device;
    }
}
