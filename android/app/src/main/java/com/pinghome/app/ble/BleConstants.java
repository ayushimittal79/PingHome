package com.pinghome.app.ble;

import java.util.UUID;

/**
 * Custom GATT UUIDs for the PingHome ESP32 wearable. These MUST match
 * wearable/pinghome-sos/include/ble_uuids.h exactly - if you regenerate one side, regenerate
 * the other. SOS is a notify-only characteristic: the ESP32 never accepts writes from the phone
 * for the SOS event itself, so a compromised/rogue phone app can't remotely trigger a wearable's
 * LED/vibration as if it were a real button press.
 */
public final class BleConstants {

    public static final UUID SOS_SERVICE_UUID =
            UUID.fromString("b6f00001-93a4-4a9b-8f21-3c9d7a2e4b10");

    /** Notify: a single byte (0x01) pushed by the ESP32 each time the physical button is pressed. */
    public static final UUID SOS_CHARACTERISTIC_UUID =
            UUID.fromString("b6f00002-93a4-4a9b-8f21-3c9d7a2e4b10");

    /** Read/Notify: 1 byte battery percentage (0-100), refreshed periodically by the firmware. */
    public static final UUID BATTERY_CHARACTERISTIC_UUID =
            UUID.fromString("b6f00003-93a4-4a9b-8f21-3c9d7a2e4b10");

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Advertised local name filter, so scanning doesn't pick up unrelated nearby BLE devices. */
    public static final String DEVICE_NAME_PREFIX = "PingHome-";

    private BleConstants() {}
}
