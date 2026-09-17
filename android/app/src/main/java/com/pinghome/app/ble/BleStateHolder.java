package com.pinghome.app.ble;

import androidx.lifecycle.MutableLiveData;

/** Cross-component bridge so any screen (ActiveTripActivity, WearableSettingsActivity) can
 * observe the wearable's live BLE connection state without binding to WearableWatchService
 * directly. The service is the sole writer; everything else only reads. */
public final class BleStateHolder {

    public static final MutableLiveData<BleConnectionState> connectionState =
            new MutableLiveData<>(BleConnectionState.DISCONNECTED);

    private BleStateHolder() {}
}
