package com.pinghome.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the entire BLE lifecycle with the ESP32 wearable: scan -> connect -> discover GATT
 * service/characteristic -> subscribe to the SOS characteristic -> detect SOS -> handle
 * disconnects with bounded auto-reconnect. This is the Android side of the mandatory physical
 * button -> BLE SOS event -> Android flow from SPEC.md; it is never replaced by a software
 * trigger in production code (WearableSettingsActivity's dev-only test button is guarded
 * separately and clearly labelled).
 *
 * All Bluetooth runtime permissions (BLUETOOTH_SCAN/BLUETOOTH_CONNECT on API 31+, location on
 * older APIs) must already be granted before calling startScanForPairing/connectToKnownDevice -
 * this class does not request permissions itself.
 */
@SuppressLint("MissingPermission") // Callers are responsible for the runtime permission checks.
public class PingHomeBleManager {

    private static final String TAG = "PingHomeBle";
    private static final long RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    public interface Listener {
        void onStateChanged(BleConnectionState state);
        void onSosReceived();
        void onBatteryUpdate(int percent);
        void onDeviceFoundForPairing(BluetoothDevice device, String name);
    }

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private String targetAddress;
    private final AtomicBoolean deliberateDisconnect = new AtomicBoolean(false);
    private int reconnectAttempts = 0;

    public PingHomeBleManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = manager != null ? manager.getAdapter() : null;
    }

    public boolean isBluetoothAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    /** Scans for any un-paired PingHome wearable advertising, for first-time setup. */
    public void startScanForPairing() {
        if (!isBluetoothAvailable()) {
            setState(BleConnectionState.DISCONNECTED);
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        setState(BleConnectionState.SCANNING);
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(BleConstants.SOS_SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(List.of(filter), settings, pairingScanCallback);
    }

    public void stopScan() {
        if (scanner != null) {
            scanner.stopScan(pairingScanCallback);
        }
    }

    /** Reconnects to a wearable already paired in a previous session, identified by its BLE MAC. */
    public void connectToKnownDevice(String macAddress) {
        if (!isBluetoothAvailable() || adapter == null) {
            setState(BleConnectionState.DISCONNECTED);
            return;
        }
        this.targetAddress = macAddress;
        deliberateDisconnect.set(false);
        reconnectAttempts = 0;
        BluetoothDevice device = adapter.getRemoteDevice(macAddress);
        connectGatt(device);
    }

    public void connect(BluetoothDevice device) {
        this.targetAddress = device.getAddress();
        deliberateDisconnect.set(false);
        reconnectAttempts = 0;
        connectGatt(device);
    }

    public void disconnect() {
        deliberateDisconnect.set(true);
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        setState(BleConnectionState.DISCONNECTED);
    }

    private void connectGatt(BluetoothDevice device) {
        setState(reconnectAttempts > 0 ? BleConnectionState.RECONNECTING : BleConnectionState.CONNECTING);
        int transport = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? BluetoothDevice.TRANSPORT_LE : 0;
        gatt = device.connectGatt(appContext, false, gattCallback, transport);
    }

    private final ScanCallback pairingScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.startsWith(BleConstants.DEVICE_NAME_PREFIX)) {
                listener.onDeviceFoundForPairing(device, name);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "BLE scan failed, error=" + errorCode);
            setState(BleConnectionState.DISCONNECTED);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0;
                setState(BleConnectionState.CONNECTED);
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close();
                if (gatt == g) gatt = null;

                if (deliberateDisconnect.get()) {
                    setState(BleConnectionState.DISCONNECTED);
                    return;
                }
                attemptReconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed, status=" + status);
                return;
            }
            BluetoothGattService service = g.getService(BleConstants.SOS_SERVICE_UUID);
            if (service == null) {
                Log.w(TAG, "PingHome SOS service not found on this device");
                return;
            }

            BluetoothGattCharacteristic sosChar = service.getCharacteristic(BleConstants.SOS_CHARACTERISTIC_UUID);
            if (sosChar != null) enableNotifications(g, sosChar);

            BluetoothGattCharacteristic batteryChar = service.getCharacteristic(BleConstants.BATTERY_CHARACTERISTIC_UUID);
            if (batteryChar != null) enableNotifications(g, batteryChar);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (BleConstants.SOS_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "SOS characteristic notified - physical button pressed on wearable");
                mainHandler.post(listener::onSosReceived);
            } else if (BleConstants.BATTERY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length > 0) {
                    int percent = value[0] & 0xFF;
                    mainHandler.post(() -> listener.onBatteryUpdate(percent));
                }
            }
        }
    };

    private void enableNotifications(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
        g.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID);
        if (descriptor == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            g.writeDescriptor(descriptor);
        }
    }

    private void attemptReconnect() {
        if (targetAddress == null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            setState(BleConnectionState.DISCONNECTED);
            return;
        }
        reconnectAttempts++;
        setState(BleConnectionState.RECONNECTING);
        mainHandler.postDelayed(() -> {
            if (deliberateDisconnect.get() || adapter == null) return;
            BluetoothDevice device = adapter.getRemoteDevice(targetAddress);
            connectGatt(device);
        }, RECONNECT_DELAY_MS);
    }

    private void setState(BleConnectionState state) {
        mainHandler.post(() -> listener.onStateChanged(state));
    }
}
