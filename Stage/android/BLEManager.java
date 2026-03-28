package icu.luxcedia.crowdq.stage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.core.content.ContextCompat;

import icu.luxcedia.crowdq.exchange.CrowdQExchange;
import icu.luxcedia.crowdq.exchange.CrowdQExchangeTag;
//import icu.luxcedia.crowdq.core.threading.SerialExecutor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;

public class BLEManager {
    private static final String TAG = "BLEManager";

    // Enums MessageType
    public enum MessageType {
        SHOW(0),
        COMMAND(1),
        SENSOR(2);

        private final int value;
        MessageType(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    // Optimization: Deny byte array
    public interface SensorCallback {
        void onSensorDataReceived(String decodedData);
    }
    private final BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback currentCallback;
    private final ParcelUuid broadcastUuid;
    private int sequence = 0;

    // Single-thread executor for ordered transmission
    private final ExecutorService serialQueue = Executors.newSingleThreadExecutor();

    public BLEManager(ParcelUuid broadcastUuid, Parcel sensorUuid, SensorCallback callback) {
        this.broadcastUuid = broadcastUuid;
        this.sensorUuid = sensorUuid;
        this.callback = callback;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        this.advertiser = (adapter != null) ? adapter.getBluetoothLeAdvertiser() : null;
    }

    public void enqueue(Context context, CrowdQExchangeTag tag, int arg, String payload) {
        serialQueue.execute(() -> {
            CrowdQExchange packet = new CrowdQExchange(sequence++, tag, arg, payload);
            String packedString = packet.pack();

            // Optimization: Encode as ASCII/UTF-8 bytes as requested
            byte[] packetBytes = packedString.getBytes(StandardCharsets.UTF_8);

            // TODO: Add fragmentation logic here for GATT if packetBytes.length > 31 bytes

            // Optimization: addServiceData() checks Android 12+ runtime permissions
            // Optimization: Packet service data, not local name
            AdvertiseData adData = new AdvertiseData.Builder()
                    .addServiceUuid(broadcastUuid)
                    // Pack data in Service Data rather than Local Name
                    .addServiceData(broadcastUuid, packetBytes)
                    .build();

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build();

            restartAdvertising(context, settings, adData);
        });
    }

    @SuppressLint("MissingPermission")
    private void restartAdvertising(Context context, AdvertiseSettings settings, AdvertiseData data) {
        if (advertiser == null || !hasBlePermissions(context)) return;

        if (currentCallback != null) {
            advertiser.stopAdvertising(currentCallback);
        }

        currentCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "Advertising started successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertising failed: " + errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, currentCallback);
    }

    private boolean hasBlePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}