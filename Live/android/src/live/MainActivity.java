// ViewController.swift

package live;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.net.Uri;
import android.widget.VideoView;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bluedog.BlueDog;
import bluedog.ShowEffects;

public class MainActivity extends AppCompatActivity implements BridgeObserver.Listener, ShowEffects {
    private static final String TAG = "MainActivity";

    private static final String DEMO_SHOW_JSON =
            "{\"title\":\"CrowdQ Demo\",\"version\":\"1.0\",\"commands\":{" +
                    "\".right\":{\"flash\":{\"intensity\":0.8,\"duration\":300},\"buzz\":{\"pattern\":2,\"duration\":200}}," +
                    "\".left\":{\"flash\":{\"intensity\":0.8,\"duration\":300},\"buzz\":{\"pattern\":2,\"duration\":200}}," +
                    "\".up\":{\"flash\":{\"intensity\":1.0,\"duration\":500},\"buzz\":{\"pattern\":3,\"duration\":300}}," +
                    "\".down\":{\"flash\":{\"intensity\":0.4,\"duration\":200},\"buzz\":{\"pattern\":1,\"duration\":150}}," +
                    "\".inward\":{\"flash\":{\"intensity\":1.0,\"duration\":700},\"buzz\":{\"pattern\":5,\"duration\":400},\"sync\":true}," +
                    "\".outward\":{\"flash\":{\"intensity\":0.6,\"duration\":250},\"buzz\":{\"pattern\":4,\"duration\":200}}" +
                    "}}";

    private BlueDog blueDog;

    private AlertDialog alert;

    private BridgeObserver observer;
    private PrimaryFragment primary;
    private SettingsFragment settings;
    private AboutFragment about;

    private SoundController soundPlayer;
    private FlashController flashPlayer;
    private ImageController imagePlayer;
    private BuzzController buzzPlayer;


    private int quadrant = 0;

    private boolean didPresent = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        quadrant = new Random().nextInt(4);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        soundPlayer = new SoundController(this);
        flashPlayer = new FlashController(this);
        imagePlayer = new ImageController();
        buzzPlayer = new BuzzController(this);

        blueDog = new BlueDog(this);
        blueDog.loadShow(DEMO_SHOW_JSON);   // pre-load so commands work immediately

        // Temporary test — fires .right command 4 seconds after launch
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "DEBUG: firing test command");
            blueDog.executeCommand(".right", 0);
        }, 4000);

        primary = new PrimaryFragment();
        settings = new SettingsFragment();
        about = new AboutFragment();

        observer = new BridgeObserver(
                java.util.UUID.fromString("0000EEEE-0000-1000-8000-00805F9B34FB"),
                this
        );

        settings.add("startup");

        setupViewPager();
        findViewById(android.R.id.content).post(this::requestBlePermissions);
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "App is coming back to the foreground!");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (observer != null) {
            observer.startScan(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        observer.stopScan(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        observer.destroy();
        soundPlayer.shutdown();
        flashPlayer.shutdown();
        imagePlayer.shutdown();
        buzzPlayer.shutdown();
        executor.shutdown();
    }

    private void setupViewPager() {
        ViewPager2 pager = findViewById(R.id.pager);
        pager.setSaveEnabled(false);
        PagerAdapter adapter = new PagerAdapter(this);

        adapter.addPage(primary);
        adapter.addPage(settings);
        adapter.addPage(about);
        pager.setAdapter(adapter);
    }

    /* Bluetooth Perms */
    private static final int BLE_PERMISSION_REQUEST_CODE = 1001;
    private void requestBlePermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
            Log.e(TAG, "Bluetooth scan permission denied");
        }
    }

    private void onBlePermissionsGranted() {
        observer.startScan(this);
    }

    public void executeCommand(String command, int argument) {
        Log.d(TAG, "executeCommand: " + command + " arg: " + argument);
        if (blueDog.isLoaded()) {
            blueDog.executeCommand(command, argument);
        }
    }

    public void loadNewShow(String jsonName) {
        Log.d(TAG, "Start loading show file: " + jsonName);
        settings.add("loading " + jsonName);

        mainHandler.post(() -> primary.toggleBouncing(false));

        executor.execute(() -> {
            try {
                URL url = new URL("https://luxcedia.icu/shows/" + jsonName + ".json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();

                if (code != 200) {
                    settings.add("failed loading " + jsonName);
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);

                settings.add("loaded " + jsonName);
                mainHandler.post(() -> settings.setShowName(jsonName));

                if (blueDog != null) {
                    blueDog.loadShow(response.toString());
                }

            } catch (Exception e) {
                settings.add("failed loading " + jsonName + ": " + e.getMessage());
                Log.e(TAG, "loadNewShow error: " + e.getMessage());
            }
        });
    }


    /* ShowEffect implementations */
    @Override
    public void flash(double intensity, int durationMs) {
        flashPlayer.addFlash(intensity, durationMs);
    }

    @Override
    public void buzz(int pattern, int durationMs) {
        buzzPlayer.addBuzz(pattern, durationMs);
    }

    @Override
    public void playSound(byte[] soundData, int durationMs) {
        soundPlayer.addSound(soundData, durationMs);
    }

    @Override
    public void showMessage(String message) {
        printer(message);
    }

    @Override
    public void playVideo(String url) {
        video(url);
    }

    public void printer(String message) {
        mainHandler.post(() -> {
            if (alert != null) alert.dismiss();
            alert = new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton("Dismiss", null)
                    .show();
        });
    }

    public void video(String path) {
        mainHandler.post(() -> {
            Uri uri = Uri.parse(path);
            VideoView videoView = new VideoView(this);
            videoView.setVideoURI(uri);

            new AlertDialog.Builder(this)
                    .setView(videoView)
                    .setPositiveButton("Close", (d, w) -> videoView.stopPlayback())
                    .show();
        });
    }

    @Override
    public void renderImage(Bitmap image, int durationMs) {
        mainHandler.post(() -> {
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(image);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(imageView)
                    .create();
            dialog.show();
            mainHandler.postDelayed(dialog::dismiss, durationMs);
        });
    }

    @Override
    public void setBackground(Bitmap image) {
        mainHandler.post(() -> {
            if (primary.getView() != null) {
                ImageView bg = primary.getView().findViewById(R.id.background);
                if (bg != null) {
                    bg.setImageBitmap(image);
                }
            }
        });
    }

    public void sync() { // DispatchGroup + 4 pipelines
        CountDownLatch latch = new CountDownLatch(4);

        imagePlayer.addSyncPoint(latch::countDown);
        soundPlayer.addSyncPoint(latch::countDown);
        flashPlayer.addSyncPoint(latch::countDown);
        buzzPlayer.addSyncPoint(latch::countDown);

        try {
            latch.await();
            Log.d(TAG, "All pipelines synced");
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Sync interrupted: " + e.getMessage());
        }
    }

    @Override
    public void onLoadShow(String showName) {
        loadNewShow(showName);
    }

    @Override
    public void onCommand(String command, int argument) {
        executeCommand(command, argument);
    }
}