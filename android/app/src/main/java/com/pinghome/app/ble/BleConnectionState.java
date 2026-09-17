package com.pinghome.app.ble;

public enum BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    // Distinguishes "we lost the link and are retrying" from a clean, deliberate disconnect -
    // the wearable status pill in the UI reads this differently (amber vs. grey).
    RECONNECTING
}
