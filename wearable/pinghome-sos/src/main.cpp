/*
 * PingHome ESP32 SOS wearable firmware.
 *
 * Scope is deliberately minimal, per SPEC.md: "ESP32 should have minimal business logic. Its
 * primary job is: physical button -> BLE SOS event -> Android." Everything else (who to notify,
 * escalation, persistence) lives on the phone/backend. This firmware:
 *   - advertises a BLE GATT service PingHome's Android app scans for,
 *   - debounces a physical SOS button and notifies a single-byte SOS event over BLE,
 *   - exposes a battery-percentage characteristic,
 *   - gives local LED feedback for button press / connection state,
 *   - reduces CPU clock and uses light sleep between actions for lower power draw.
 *
 * See ../../docs/wearable.md for wiring, the BLE protocol, and flashing instructions.
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <esp_sleep.h>

#include "ble_uuids.h"

// ---- Pin configuration (see docs/wearable.md for the wiring diagram) ----------------------
static const int SOS_BUTTON_PIN = 4;      // Button to GND, INPUT_PULLUP - press = LOW.
static const int STATUS_LED_PIN = 2;      // Most ESP32 dev boards break out an onboard LED here.
static const int BATTERY_ADC_PIN = 34;    // ADC1 channel behind a 2:1 voltage divider from VBAT.

// ---- Timing ---------------------------------------------------------------------------------
static const unsigned long DEBOUNCE_MS = 300;        // Ignore bounce noise on the raw edge.
static const unsigned long SOS_COOLDOWN_MS = 2000;    // "Prevent accidental repeated events."
static const unsigned long BATTERY_UPDATE_MS = 60000; // Refresh + notify battery % once a minute.
static const unsigned long ADVERTISE_BLINK_MS = 1000;

BLEServer* server = nullptr;
BLECharacteristic* sosCharacteristic = nullptr;
BLECharacteristic* batteryCharacteristic = nullptr;

volatile bool buttonInterruptFlag = false;
unsigned long lastDebounceTime = 0;
unsigned long lastSosSentAt = 0;
unsigned long lastBatteryUpdate = 0;
unsigned long lastAdvertiseBlink = 0;
bool ledOn = false;
bool deviceConnected = false;

void IRAM_ATTR onButtonInterrupt() {
    buttonInterruptFlag = true;
}

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* srv) override {
        deviceConnected = true;
        digitalWrite(STATUS_LED_PIN, HIGH); // Solid LED = phone connected.
    }

    void onDisconnect(BLEServer* srv) override {
        deviceConnected = false;
        digitalWrite(STATUS_LED_PIN, LOW);
        // Advertising stops on disconnect by default - restart it so the wearable is
        // reconnectable without a manual reset (supports Android's auto-reconnect behaviour).
        BLEDevice::startAdvertising();
    }
};

void setupBle() {
    String deviceName = String(PINGHOME_DEVICE_NAME_PREFIX) + String((uint32_t)(ESP.getEfuseMac() & 0xFFFF), HEX);
    BLEDevice::init(deviceName.c_str());

    server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    BLEService* service = server->createService(PINGHOME_SERVICE_UUID);

    sosCharacteristic = service->createCharacteristic(
        PINGHOME_SOS_CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
    );
    sosCharacteristic->addDescriptor(new BLE2902());

    batteryCharacteristic = service->createCharacteristic(
        PINGHOME_BATTERY_CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
    );
    batteryCharacteristic->addDescriptor(new BLE2902());

    service->start();

    BLEAdvertising* advertising = BLEDevice::getAdvertising();
    advertising->addServiceUUID(PINGHOME_SERVICE_UUID);
    advertising->setScanResponse(true);
    // Helps some Android BLE stacks negotiate a connection faster/more reliably.
    advertising->setMinPreferred(0x06);
    advertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
}

uint8_t readBatteryPercent() {
    // Voltage divider (2x 100k) halves VBAT into the ADC's 0-3.3V range - adjust the multiplier
    // and the two threshold constants below to match your actual battery/divider hardware.
    int raw = analogRead(BATTERY_ADC_PIN);
    float voltage = (raw / 4095.0f) * 3.3f * 2.0f;

    const float EMPTY_V = 3.3f;
    const float FULL_V = 4.2f;
    float percent = (voltage - EMPTY_V) / (FULL_V - EMPTY_V) * 100.0f;
    if (percent < 0) percent = 0;
    if (percent > 100) percent = 100;
    return (uint8_t)percent;
}

void sendSos() {
    uint8_t payload = 0x01;
    sosCharacteristic->setValue(&payload, 1);
    if (deviceConnected) {
        sosCharacteristic->notify();
    }
    // Local feedback regardless of BLE connection state - the wearer needs confirmation the
    // button registered even if the phone is out of range at that instant.
    for (int i = 0; i < 6; i++) {
        digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN));
        delay(80);
    }
    digitalWrite(STATUS_LED_PIN, deviceConnected ? HIGH : LOW);
}

void handleButton() {
    if (!buttonInterruptFlag) return;
    buttonInterruptFlag = false;

    unsigned long now = millis();
    if (now - lastDebounceTime < DEBOUNCE_MS) return;
    lastDebounceTime = now;

    if (digitalRead(SOS_BUTTON_PIN) != LOW) return; // Confirm still pressed after debounce window.

    if (now - lastSosSentAt < SOS_COOLDOWN_MS) return; // Swallow rapid repeats from one press.
    lastSosSentAt = now;

    sendSos();
}

void updateBatteryIfDue() {
    unsigned long now = millis();
    if (now - lastBatteryUpdate < BATTERY_UPDATE_MS && lastBatteryUpdate != 0) return;
    lastBatteryUpdate = now;

    uint8_t percent = readBatteryPercent();
    batteryCharacteristic->setValue(&percent, 1);
    if (deviceConnected) {
        batteryCharacteristic->notify();
    }
}

void blinkWhileAdvertising() {
    if (deviceConnected) return;
    unsigned long now = millis();
    if (now - lastAdvertiseBlink < ADVERTISE_BLINK_MS) return;
    lastAdvertiseBlink = now;
    ledOn = !ledOn;
    digitalWrite(STATUS_LED_PIN, ledOn ? HIGH : LOW);
}

void setup() {
    pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);
    analogReadResolution(12);

    attachInterrupt(digitalPinToInterrupt(SOS_BUTTON_PIN), onButtonInterrupt, FALLING);

    // Low-power: this firmware does nothing CPU-intensive, so run well below the 240MHz default.
    setCpuFrequencyMhz(80);

    setupBle();
}

void loop() {
    handleButton();
    updateBatteryIfDue();
    blinkWhileAdvertising();

    // Light sleep between polls instead of a busy-wait loop. The button is interrupt-driven
    // (GPIO wake source below) so a press is still caught promptly even while sleeping; this is
    // the "low-power/sleep behaviour" SPEC.md calls for without dropping the BLE connection,
    // which a full deep-sleep cycle would do.
    esp_sleep_enable_timer_wakeup(50 * 1000); // 50ms
    esp_sleep_enable_ext0_wakeup((gpio_num_t)SOS_BUTTON_PIN, 0);
    esp_light_sleep_start();
}
