package icu.luxcedia.crowdq.live;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BridgeObserver {
    private static final String TAG = "BridgeObserver";

    // Optimization: dispatch handling to single-thread executor
    private final ExecutorService serialQueue = Executors.newSingleThreadExecutor();

    private final LiveCoordinator coordinator;
    private int lastSequence = -1;

    // Constructor injection
    public BridgeObserver(LiveCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    private final ParcelUuid phonesUuid = ParcelUuid.fromString("0000EEEE-0000-1000-8000-00805f9b34fb");
    private BluetoothLeScanner scanner;

    private String currentShowName;

    public void startObserving(Context context) {
        if (!hasScanPermissions(context)) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            scanner = adapter.getBluetoothLeScanner();

            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(phonesUuid)
                    .build();

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord record = result.getScanRecord();
            if (record == null) return;

            // Extract packet directly from service data
            byte[] serviceData = record.getServiceData(phonesUuid);
            if (serviceData != null) {
                // Parse once using UTF-8 string, allowing CrowdQExchange to parse without substrings
                String payloadStr = new String(serviceData, StandardCharsets.UTF_8);
                CrowdQExchange exchange = CrowdQExchange.parse(payloadStr);

                if (exchange != null) {
                    // Dispatch to single thread executor
                    serialQueue.execute(() -> handleExchange(exchange));
                }
            }
        }
    };

    private void handleExchange(CrowdQExchange exchange) {
        // Enforce ordering
        if (exchange.getSequence() > lastSequence) {
            lastSequence = exchange.getSequence();
            Log.d(TAG, "Processed: " + exchange.toString());

            coordinator.onExchangePacket(exchange);
        }
        else {
            Log.d(TAG, "Ignored out-of-order packet. Current: " + exchange.getSequence() + ", Last: " + lastSequence);
        }
    }

    private boolean hasScanPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}