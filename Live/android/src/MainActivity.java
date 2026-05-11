// ViewController.swift

package live;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.net.Uri;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements BridgeObserver.Listener {
    private static final String TAG = "MainActivity";

    private BridgeObserver observer;
    private PrimaryFragment primary;
    private SettingsFragment settings;
    private AboutFragment about;

    private SoundController soundPlayer;
    private FlashController flashPlayer;
    private ImageController imagePlayer;
    private BuzzController buzzPlayer;

    private int quadrant = 0;

    private AlertDialog alert;
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

        primary = new PrimaryFragment();
        settings = new SettingsFragment();
        about = new AboutFragment();

        observer = new BridgeObserver(
                java.util.UUID.fromString("0000EEEE-0000-1000-8000-00805F9B34FB"),
                this
        );

        settings.add("startup");

        setupViewPager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "App is coming back to the foreground!");
        observer.startScan(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        observer.stopScan(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        soundPlayer.shutdown();
        flashPlayer.shutdown();
        imagePlayer.shutdown();
        buzzPlayer.shutdown();
        executor.shutdown();
    }

    private void setupViewPager() {
        ViewPager2 pager = findViewById(R.id.pager);
        PagerAdapter adapter = new PagerAdapter(this);
        adapter.addPage(primary);
        adapter.addPage(settings);
        adapter.addPage(about);
        pager.setAdapter(adapter);
    }

    public void executeCommand(String command, int argument) {
        // BlueDog translated
        Log.d(TAG, "executeCommand: " + command + " arg: " + argument);
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

                // Bluedog wired here once translated
            } catch (Exception e) {
                settings.add("failed loading " + jsonName + ": " + e.getMessage());
                Log.e(TAG, "loadNewShow error: " + e.getMessage());
            }
        });
    }

    public void flasher(double intensity, int duration) {
        Log.d(TAG, "flasher " + intensity + " " + duration);
        flashPlayer.addFlash(intensity, duration);
    }

    public void render(int imageResId, int duration) {
        imagePlayer.addImage(this, imageResId, duration);
    }

    public void player(byte[] sound, int duration) {
        soundPlayer.addSound(sound, duration);
    }

    public void buzzer(int pattern, int duration) {
        buzzPlayer.addBuzz(pattern, duration);
    }

    public void setBackground(int imageResId) {
        mainHandler.post(() -> {
            if (primary.getView() != null) {
                primary.getView().findViewById(R.id.background)
                        .setBackgroundResource(imageResId);
            }
        });
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