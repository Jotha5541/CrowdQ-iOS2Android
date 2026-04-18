package stage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /* GATT Implementation */
    private final UUID charUuid = UUID.fromString("11112222-3333-4444-5555-666677778888");
    private BluetoothManager bluetoothManager;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic exchangeCharacteristic;
    private final Set<BluetoothDevice> connectedClients = new HashSet<>();

    public BLEManager(ParcelUuid broadcastUuid, Parcel sensorUuid, SensorCallback callback) {
        this.broadcastUuid = broadcastUuid;
        this.sensorUuid = sensorUuid;
        this.callback = callback;

        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.advertiser = (adapter != null) ? adapter.getBluetoothLeAdvertiser() : null;
    }


    /* GATT Server Setup */
    @SuppressLint("MissingPermission")
    public void openGattServer(Context context) {
        if (gattServer != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for GATT Server");
            return;
        }

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) return;

        exchangeCharacteristic = new BluetoothGattCharacteristic(
                charUuid,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService service = new BluetoothGattService(
                broadcastUuid.getUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );
        service.addCharacteristic(exchangeCharacteristic);

        gattServer.addService(service);
        Log.d(TAG, "GATT Server Opened");
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            serialQueue.execute(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedClients.add(device);
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedClients.remove(device);
                }
            });
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (charUuid.equals(characteristic.getUuid())) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
            else {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }
    };

    /* Broadcast and Notify GATT */
    public void enqueue(Context context, CrowdQExchangeTag tag, int arg, String payload) {
        serialQueue.execute(() -> {
            CrowdQExchange packet = new CrowdQExchange(sequence++, tag, arg, payload);
            String packedString = packet.pack();
            byte[] packetBytes = packedString.getBytes(StandardCharsets.US_ASCII);

            // Optimization: Notifying GATT after larger package load
            if (gattServer != null && exchangeCharacteristic != null) {
                exchangeCharacteristic.setValue(packetBytes);
                for (BluetoothDevice client : connectedClients) {
                    gattServer.notifyCharacteristicChanged(client);
                }
            }

            // Package Fragmentation and Size check
            if (packetBytes.length > 24) {
                Log.w(TAG, "Payload exceeds Advertising limit. Switching to GATT");
                return;
            }

            // Optimization: addServiceData() checks Android 12+ runtime permissions
            // Optimization: Packet service data, not local name
            AdvertiseData adData = new AdvertiseData.Builder()
                    .addServiceUuid(broadcastUuid)
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

    /* Scanning */
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

                    serialQueue.execute(() -> {
                        callback.onSensorDataReceived(name);
                    });
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

        if (gattServer != null) {
            @SuppressLint("MissingPermission")
//            BluetoothManager bm = gattServer;
            gattServer.close();
            gattServer = null;
        }
    }
}