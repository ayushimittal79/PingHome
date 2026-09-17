# ESP32 wearable

`wearable/pinghome-sos/` — Arduino framework, built with PlatformIO. The firmware is intentionally minimal, per SPEC.md: its only job is turning a physical button press into a BLE notification. Everything else (which trip, which watchers, escalation) lives on the phone/backend.

## Wiring

| Component | ESP32 pin | Notes |
|---|---|---|
| SOS push button | GPIO 4 | One leg to GPIO 4, other leg to GND. Firmware uses `INPUT_PULLUP` - no external resistor needed. |
| Status LED | GPIO 2 | Most ESP32 dev boards already have one broken out here; add your own LED + ~220Ω resistor to GND if not. |
| Battery voltage sense | GPIO 34 (ADC1) | Through a 2:1 resistor divider (two equal resistors, e.g. 100kΩ + 100kΩ) from VBAT to GND, tap between them into GPIO 34. Omit this circuit and the battery characteristic will just read a constant/inaccurate value - it's not required for the SOS path to work. |

Power: a single-cell LiPo (3.7V nominal) through a TP4056-style charge/protection board into the ESP32's `VIN`/`5V` (boost) or `3V3` pin depending on your specific board - check your board's regulator input range before wiring a battery directly. This is what makes the "keychain-style wearable" physically plausible: button + LED + battery + a small ESP32 dev board (or a bare ESP32-WROOM module for a more compact build) in a 3D-printed or off-the-shelf enclosure.

## BLE protocol

Custom GATT service, UUIDs defined once in `wearable/pinghome-sos/include/ble_uuids.h` and mirrored exactly in `android/.../ble/BleConstants.java` - if you change one, change the other.

| Handle | UUID | Properties | Payload |
|---|---|---|---|
| Service | `b6f00001-93a4-4a9b-8f21-3c9d7a2e4b10` | | |
| SOS characteristic | `b6f00002-93a4-4a9b-8f21-3c9d7a2e4b10` | Notify, Read | 1 byte, `0x01` on a button press |
| Battery characteristic | `b6f00003-93a4-4a9b-8f21-3c9d7a2e4b10` | Notify, Read | 1 byte, 0-100 (%) |

The SOS characteristic is **notify-only from the firmware's perspective** - it never accepts writes from the phone, so a compromised or rogue app on the phone can't remotely fake a physical button press. The device advertises as `PingHome-<4 hex chars from its MAC>`; the Android app filters scan results by the `PingHome-` prefix and the service UUID.

## Firmware behavior

- **Debounce**: a `FALLING`-edge GPIO interrupt sets a flag; the main loop ignores repeat flags within 300ms, then re-checks the pin is still low before treating it as a real press (`DEBOUNCE_MS` in `main.cpp`).
- **Duplicate-event guard**: even a legitimate press won't fire again within 2 seconds (`SOS_COOLDOWN_MS`) - on top of the backend's own SOS dedup window, so a nervous double/triple-press doesn't spam watchers.
- **Local feedback**: the status LED blinks rapidly for ~0.5s on every SOS trigger, whether or not a phone is currently connected - the wearer gets confirmation the button registered even out of BLE range.
- **Connection-state LED**: solid on while a phone is connected, slow blink while advertising/disconnected.
- **Low power**: CPU clock is dropped to 80MHz (from the 240MHz default); the button is interrupt-driven rather than polled; the main loop uses `esp_light_sleep_start()` between checks so the MCU isn't busy-waiting. This is a deliberate middle ground - a full deep-sleep cycle would drop the BLE connection entirely (and miss the "always reachable" point of a safety wearable), so this firmware trades some power savings for staying connected.
- **Auto-advertise on disconnect**: `ServerCallbacks::onDisconnect` restarts advertising immediately, which is what lets Android's bounded auto-reconnect (`PingHomeBleManager`) actually succeed without a manual reset on the wearable.

## Building and flashing

```bash
cd wearable/pinghome-sos
pio run                 # compile
pio run -t upload       # flash over USB (board must be connected; hold BOOT if your board needs it)
pio device monitor -b 115200   # serial log
```

## Pairing from the app

Profile tab → "PingHome wearable" → Scan. The app filters for the `PingHome-` name prefix and the service UUID, so unrelated nearby BLE devices won't show up. Tapping a found device registers it with the backend (`POST /api/wearables`) and starts a foreground service (`WearableWatchService`) that maintains the BLE connection and relays any SOS notification to `POST /api/sos/wearable`.

## Development-only test trigger

`WearableSettingsActivity` shows a "(Dev only) Simulate wearable SOS" button, but **only in debug builds** (`BuildConfig.DEBUG`) - it calls the same `/api/sos/wearable` endpoint with a fake BLE identifier, purely so the SOS-relay and escalation flow can be exercised without hardware on hand while developing the app itself. It does not appear in release builds and is not a substitute for testing against the real ESP32 - see [testing.md](testing.md) for what still needs to happen on real hardware before this feature can be called verified.
