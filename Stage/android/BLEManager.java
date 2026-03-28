package icu.luxcedia.crowdq.stage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
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
    private final ParcelUuid sensorUuid;
    private final SensorCallback callback;

    private boolean listening = false;
    private int sequence = 0;
    private String lastName = "";
    private int count = 0;

    // Single-thread executor for ordered transmission
    private final ExecutorService serialQueue = Executors.newSingleThreadExecutor();

    // Android BLE components
    private final BluetoothAdapter adapter;
    private final BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private AdvertiseCallback advertiseCallback;
    private ScanCallback scanCallback;

    public BLEManager(ParcelUuid broadcastUuid, Parcel sensorUuid, SensorCallback callback) {
        this.broadcastUuid = broadcastUuid;
        this.sensorUuid = sensorUuid;
        this.callback = callback;

        this.adapter = BlueToothAdapter.getDefaultAdapter();
        this.advertiser = (adapter != null) ? adapter.getBluetoothLeAdvertiser() : null;
    }

    public void enqueue(Context context, CrowdQExchangeTag tag, int arg, String payload) {
        serialQueue.execute(() -> {
            CrowdQExchange packet = new CrowdQExchange(sequence++, tag, arg, payload);
            String packedString = packet.pack();

            // Optimization: Encode as ASCII/UTF-8 bytes as requested
            byte[] packetBytes = packedString.getBytes(StandardCharsets.US_ASCII);

            // TODO: Add fragmentation for GATT if packetBytes.length > 31 bytes

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
        if (advertiser == null || !hasBlePermissions(context, Manifest.permission.BLUETOOTH_ADVERTISE)) return;

        if (advertiseCallback != null) {
            advertiser.stopAdvertising(advertiseCallback);
        }

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "Advertising started successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertising failed: " + errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    public void setListening(Context context, boolean isListening) {
        this.listening = isListening;
        if (listening) {
            startScanning(context);
        }
        else {
            stopScanning(context);
        }
    }

    // Scanning
    @SuppressLint("MissingPermission")
    private void startScanning(Context context) {
        if (adapter == null || !hasBlePermissions(context, Manifest.permission.BLUETOOTH_SCAN)) return;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(sensorUuid)
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanCallback = new ScanCallback() {
            public void onScanResult(int callbackType, ScanResult result) {
                if (!listening) return;

                ScanRecord record = result.getScanRecord();
                if (record == null) return;

                // Extract from service data
                byte[] serviceData = record.getServiceData(sensorUuid);
                if (serviceData == null || serviceData.length == 0) return;

                String name = new String(serviceData, StandardCharsets.US_ASCII);

                if (!name.equals(lastName)) {
                    lastName = name;
                    Log.d(TAG, "Sensor Count: " + count + " Name: " + name);
                    count++;

                    // Serial queue callback
                    serialQueue.execute(() -> {
                        callback.onSensorDataReceived(name);
                    })
                }
            }
        };

        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        Log.d(TAG, "Sensor scanning started");
    }

    @SuppressLint("MissingPermission")
    private void stopScanning(Context context) {
        if (scanner != null && scanCallback != null && hasBlePermissions(context, Manifest.permission.BLUETOOTH_SCAN)) {
            scanner.stopScan(scanCallback);
            Log.d(TAG, "Sensor scanning stopped");
        }
    }

    private boolean hasBlePermissions(Context context, String android12Permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, android12Permission) == PackageManager.PERMISSION_GRANTED;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void destroy() {
        serialQueue.shutdown();
    }
}