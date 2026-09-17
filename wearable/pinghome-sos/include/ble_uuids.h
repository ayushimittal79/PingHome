#pragma once

// MUST match android/app/src/main/java/com/pinghome/app/ble/BleConstants.java exactly.
// If you regenerate one side, regenerate the other.

#define PINGHOME_SERVICE_UUID          "b6f00001-93a4-4a9b-8f21-3c9d7a2e4b10"
#define PINGHOME_SOS_CHAR_UUID         "b6f00002-93a4-4a9b-8f21-3c9d7a2e4b10"
#define PINGHOME_BATTERY_CHAR_UUID     "b6f00003-93a4-4a9b-8f21-3c9d7a2e4b10"

// Advertised name prefix the Android app filters on while scanning for pairing.
// Append a short per-unit suffix (e.g. "PingHome-A1B2") so multiple wearables don't collide.
#define PINGHOME_DEVICE_NAME_PREFIX    "PingHome-"
