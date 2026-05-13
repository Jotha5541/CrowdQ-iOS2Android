// ViewController.swift
// SceneDelegate.swift
package stage;

import static android.provider.Settings.*;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import exchange.CrowdQExchangeTag;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import android.os.ParcelUuid;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public enum Direction {
        NONE(0), LEFT(1), RIGHT(2), UP(3), DOWN(4), INWARD(5), OUTWARD(6);
        public final int value;
        Direction(int value) { this.value = value; }
    }

    public static class Telemetry {
        public final int sensor;
        public final short ax, ay, az;

        public Telemetry(byte[] data) {
            if (data.length < 7) {
                throw new IllegalArgumentException("Invalid telemetry data length");
            }

            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            this.sensor = buffer.get(0) & 0xFF; // Converts 8-bit to Java int
            this.ax = buffer.getShort(1);
            this.ay = buffer.getShort(3);
            this.az = buffer.getShort(5);
        }
    }

    public BLEManager bleClient;
    private final ExecutorService bleExecutor = Executors.newSingleThreadExecutor();
    private Direction lastDirection = Direction.NONE;


    private final ParcelUuid phonesUUID = new ParcelUuid(UUID.fromString("0000EEEE-0000-1000-8000-00805F9B34FB"));
    private final ParcelUuid sensorUUID = new ParcelUuid(UUID.fromString("0000BBBB-0000-1000-8000-00805F9B34FB"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Device ID
        @SuppressLint("HardwareIds")
        String deviceUUID = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        Log.d(TAG, "Device UUID: " + deviceUUID);

        // Prevent sleeping when active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // SharedPreferences
        SharedPreferences prefs = getSharedPreferences("CrowdQPrefs", MODE_PRIVATE);
        String email = prefs.getString("email", "");
        if (!email.isEmpty()) {
            // Note: Changes Page1 UI here
        }

        // Initializes BLE Manager
        bleClient = new BLEManager(phonesUUID, sensorUUID, data -> processTelemetry(data.getBytes()));

        findViewById(android.R.id.content).post(() -> {
            setupViewPager();
            requestBlePermissions();
            findViewById(R.id.loadingIndicator).setVisibility(View.GONE);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (bleClient != null) {
            bleClient.setListening(this, true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bleClient != null) {
            bleClient.setListening(this, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "App is coming back to the foreground!");

    }

    @Override
    protected void onPause() {
        super.onPause();
//        Log.w(TAG, "onPause called", new Throwable("onPause stack trace"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleExecutor.shutdown();
        if (bleClient != null) bleClient.destroy();
    }

    private void processTelemetry(byte[] rawData) {
        try {
            Telemetry telemetry = new Telemetry(rawData);
            double gravityScale = 2.0;
            int threshold = (int) ((gravityScale / 2.0) * 512.0); // 2 G's

            Direction heading = lastDirection;

            int axMag = Math.abs(telemetry.ax);
            int ayMag = Math.abs(telemetry.ay);
            int azMag = Math.abs(telemetry.az);

            if (axMag > threshold && axMag > ayMag && axMag > azMag) {
                heading = (telemetry.ax < 0) ? Direction.LEFT : Direction.RIGHT;
            }
            else if (ayMag > threshold && ayMag > axMag && ayMag > azMag) {
                heading = (telemetry.ay < 0) ? Direction.UP : Direction.DOWN;
            }
            else if (azMag > threshold && azMag > axMag && azMag > ayMag) {
                heading = (telemetry.az < 0) ? Direction.INWARD : Direction.OUTWARD;
            }

            if (heading != lastDirection) {
                lastDirection = heading;
                String payload = "." + heading.name().toLowerCase();
                Log.d(TAG, String.format("Command: %02d%s", telemetry.sensor, payload));

                // Enqueue command to connected clients
                if (bleClient != null) {
                    bleClient.enqueue(this, CrowdQExchangeTag.COMMAND, telemetry.sensor, payload);
                }
            }
        }
        catch (IllegalArgumentException e) {
            Log.e(TAG, "Malformed Telemetry Packet", e);
        }
    }

    private void setupViewPager() {
        ViewPager2 pager = findViewById(R.id.pager);
        pager.setSaveEnabled(false);
        PagerAdapter adapter = new PagerAdapter(this);

        Page1Fragment page1 = new Page1Fragment();
        page1.setMain(this);

        SettingsFragment settingsFragment = new SettingsFragment();
        settingsFragment.setMain(this);

        adapter.addPage(page1);
        adapter.addPage(settingsFragment);
        adapter.addPage(new ButtonGridFragment());
        pager.setAdapter(adapter);
    }

    /* Bluetooth Permissions */
    private static final int BLE_PERMISSION_REQUEST_CODE = 1001;
    private void requestBlePermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Bluetooth perms
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        }
        else {
            // Android 6-11: location permission gates BLE scanning
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (needed.isEmpty()) {
            onBlePermissionsGranted();
        }
        else {
            ActivityCompat.requestPermissions(
                    this,
                    needed.toArray(new String[0]),
                    BLE_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != BLE_PERMISSION_REQUEST_CODE) return;

        boolean allGranted = grantResults.length > 0;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            onBlePermissionsGranted();
        }
        else {
            Log.e("Main Activity", "Bluetooth permissions denied - BLE features disabled");
        }
    }

    private void onBlePermissionsGranted() {
        bleExecutor.execute(() -> bleClient.openGattServer(this));
        bleClient.setListening(this, true);
    }

    public void setListening (boolean listening) {
        if (bleClient != null) {
            bleClient.setListening(this, listening);
        }
    }

    public void enqueue(CrowdQExchangeTag tag, int arg, String payload) {
        if (bleClient != null) {
            bleClient.enqueue(this, tag, arg, payload);
        }
    }

    public String getEmail() {
        SharedPreferences prefs = getSharedPreferences("CrowdQPrefs", MODE_PRIVATE);
        return prefs.getString("email", "");
    }
}
