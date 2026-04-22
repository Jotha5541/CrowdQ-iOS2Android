package live;

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


import exchange.CrowdQExchange;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

public class BridgeObserver {
    private static final String TAG = "BridgeObserver";

    public interface Listener {
        void onLoadShow(String showName);
        void onCommand(String command, int argument);
    }

    private final UUID phonesUuid;
    private final Listener listener;

    // Optimization: dispatch handling to single-thread executor
    private final SerialExecutor serialExecutor = new SerialExecutor("bridge-observer");

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private int lastSequence = -1;
    private String currentShowName;

    // Constructor injection
    public BridgeObserver(UUID phonesUuid, Listener listener) {
        this.phonesUuid = phonesUuid;
        this.listener = listener;
    }

    public void startScan(Context context) {
        if (!hasScanPermissions(context)) {
            Log.e(TAG, "Missing Bluetooth Scan permissions.");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;

        ScanFilter filter = new ScanFilter.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handleExchange(result);
            }
        };
        @SuppressLint("MissingPermission")
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
    }

    public void stopScan() {
        if (scanner != null && scanCallback != null) {
            @SuppressLint("Missing Permission")
            scanner.stopScan(scanCallback);
        }
        serialExecutor.shutdown();
    }

    // Handling incoming telemetry
    private void handleExchange(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record == null) return;

        /* Extract packets from service data */
        byte[] packetBytes = record.getServiceData(new ParcelUuid(phonesUuid));
        if (packetBytes == null || packetBytes.length < 8) return;

        // Optimization: Uses ASCII
        String packet = new String(packetBytes, StandardCharsets.US_ASCII);
        CrowdQExchange exchange = CrowdQExchange.parse(packet);
        if (exchange == null) return;

        // Optimization: Dispatch handling to single-thread queue
        serialExecutor.execute(() -> {
            if (exchange.getSequence() <= lastSequence) {
                Log.d(TAG, "Ignored out-of-order packet. Current: " + exchange.getSequence() + ", Last: " + lastSequence);
                return;
            }

            lastSequence = exchange.getSequence();
            Log.d(TAG, "Processed: " + exchange.toString());

            switch(exchange.getTag()) {
                case LOAD:
                    if (currentShowName == null || !currentShowName.equals(exchange.getPayload())) {
                        currentShowName = exchange.getPayload();
                        listener.onLoadShow(currentShowName);
                    }
                    break;
                case COMMAND:
                    listener.onCommand(exchange.getPayload(), exchange.getArgument());
                    break;
                case RESTART:
                case SHOW:
                case SHOWDATA:
                default:
                    break;
            }
        });
    }
  private boolean hasScanPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}