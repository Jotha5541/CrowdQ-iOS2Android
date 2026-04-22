// ViewController.swift

package stage;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.crowdq.exchange.CrowdQExchangeTag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
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

    private BLEManager bleClient;
    private Direction lastDirection = Direction.NONE;
    private String deviceUUID;

    private ViewPager2 pager;
    private double gravityScale = 2.0;


    private final ParcelUuid phonesUUID = new ParcelUuid(UUID.fromString("0000EEEE-0000-1000-8000-00805F9B34FB"));
    private final ParcelUuid sensorUUID = new ParcelUuid(UUID.fromString("0000BBBB-0000-1000-8000-00805F9B34FB"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Device ID
        deviceUUID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
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
        bleClient = new BLEManager(phonesUUID, sensorUUID, this::processTelemetry);

        // Setup UI (Pager logic)
        setupViewPager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "App is coming back to the foreground!");

        if (bleClient != null) {
            bleClient.openGattServer(this);
            bleClient.setListening(this, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bleClient != null) {
            bleClient.setListening(this, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleClient != null) {
            bleClient.destroy();
        }
    }

    private void processTelemetry(byte[] rawData) {
        try {
            Telemetry telemetry = new Telemetry(rawData);
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

        PagerAdapter adapter = new PagerAdapter(this);

        adapter.addPage(new Page1Fragment());
        adapter.addPage(new SettingsFragment());
        adapter.addPage(new ButtonGridFragment());

        pager.setAdapter(adapter);
    }
}
