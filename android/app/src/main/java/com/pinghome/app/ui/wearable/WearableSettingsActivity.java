package com.pinghome.app.ui.wearable;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pinghome.app.BuildConfig;
import com.pinghome.app.R;
import com.pinghome.app.ble.BleConnectionState;
import com.pinghome.app.ble.BleStateHolder;
import com.pinghome.app.ble.PingHomeBleManager;
import com.pinghome.app.ble.WearableWatchService;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.repository.SosRepository;
import com.pinghome.app.data.repository.WearableRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WearableSettingsActivity extends AppCompatActivity implements PingHomeBleManager.Listener {

    private WearableRepository wearableRepository;
    private PingHomeBleManager bleManager;
    private SessionManager session;

    private final Map<String, BluetoothDevice> foundDevices = new LinkedHashMap<>();
    private FoundDeviceAdapter deviceAdapter;

    private View connectionDot;
    private TextView connectionStatusText;
    private View scanProgress;
    private View unpairButton;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (Boolean.TRUE.equals(grants.getOrDefault(bluetoothScanPermission(), true))) startScan();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wearable_settings);
        setTitle("PingHome wearable");

        wearableRepository = new WearableRepository(this);
        bleManager = new PingHomeBleManager(this, this);
        session = SessionManager.getInstance(this);

        connectionDot = findViewById(R.id.connectionDot);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        scanProgress = findViewById(R.id.scanProgress);
        unpairButton = findViewById(R.id.unpairButton);

        RecyclerView recyclerView = findViewById(R.id.foundDevicesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new FoundDeviceAdapter();
        recyclerView.setAdapter(deviceAdapter);

        findViewById(R.id.scanButton).setOnClickListener(v -> requestPermissionsThenScan());
        unpairButton.setOnClickListener(v -> unpair());

        if (BuildConfig.DEBUG) {
            View devButton = findViewById(R.id.devTestSosButton);
            devButton.setVisibility(View.VISIBLE);
            devButton.setOnClickListener(v -> simulateWearableSos());
        }

        BleStateHolder.connectionState.observe(this, this::renderConnectionState);

        boolean paired = session.getPairedBleAddress() != null;
        unpairButton.setVisibility(paired ? View.VISIBLE : View.GONE);
    }

    private void requestPermissionsThenScan() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) {
            startScan();
        } else {
            permissionLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private String bluetoothScanPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_SCAN : Manifest.permission.ACCESS_FINE_LOCATION;
    }

    private void startScan() {
        foundDevices.clear();
        deviceAdapter.notifyDataSetChanged();
        scanProgress.setVisibility(View.VISIBLE);
        bleManager.startScanForPairing();

        // Stop scanning after 15s so we don't drain the battery indefinitely.
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            bleManager.stopScan();
            scanProgress.setVisibility(View.GONE);
        }, 15_000);
    }

    @Override
    public void onDeviceFoundForPairing(BluetoothDevice device, String name) {
        if (!foundDevices.containsKey(device.getAddress())) {
            foundDevices.put(device.getAddress(), device);
            runOnUiThread(() -> deviceAdapter.notifyDataSetChanged());
        }
    }

    private void pairWithDevice(BluetoothDevice device) {
        bleManager.stopScan();
        String name = device.getName() != null ? device.getName() : "PingHome wearable";

        wearableRepository.pair(name, device.getAddress(), result -> {
            if (result.success) {
                session.setPairedBleAddress(device.getAddress());
                unpairButton.setVisibility(View.VISIBLE);
                ContextCompat.startForegroundService(this, new Intent(this, WearableWatchService.class));
                Toast.makeText(this, "Paired with " + name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Pairing failed: " + result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void unpair() {
        session.setPairedBleAddress(null);
        stopService(new Intent(this, WearableWatchService.class));
        unpairButton.setVisibility(View.GONE);
        Toast.makeText(this, "Wearable unpaired.", Toast.LENGTH_SHORT).show();
    }

    private void simulateWearableSos() {
        new SosRepository(this).triggerWearableSos(session.getActiveTripId(), 0, 0, "DEV-SIMULATED", result ->
                Toast.makeText(this, result.success ? "Simulated SOS sent." : result.errorMessage, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onStateChanged(BleConnectionState state) {
        renderConnectionState(state);
    }

    private void renderConnectionState(BleConnectionState state) {
        int colorRes = switch (state) {
            case CONNECTED -> R.color.safe_green;
            case CONNECTING, SCANNING, RECONNECTING -> R.color.warning_amber;
            default -> R.color.charcoal_700;
        };
        connectionDot.setBackgroundColor(getColor(colorRes));
        connectionStatusText.setText(switch (state) {
            case CONNECTED -> "Connected";
            case CONNECTING -> "Connecting...";
            case SCANNING -> "Scanning...";
            case RECONNECTING -> "Reconnecting...";
            case DISCONNECTED -> session.getPairedBleAddress() != null ? "Paired, not connected" : "No wearable paired";
        });
    }

    @Override
    public void onSosReceived() {
        // Handled by WearableWatchService, which owns the live connection while this screen is
        // just for pairing/status.
    }

    @Override
    public void onBatteryUpdate(int percent) {}

    private class FoundDeviceAdapter extends RecyclerView.Adapter<FoundDeviceAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BluetoothDevice device = new ArrayList<>(foundDevices.values()).get(position);
            String name = device.getName() != null ? device.getName() : device.getAddress();
            holder.text.setText(name);
            holder.itemView.setOnClickListener(v -> pairWithDevice(device));
        }

        @Override
        public int getItemCount() {
            return foundDevices.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            Holder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
