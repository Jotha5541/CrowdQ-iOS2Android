// SettingsViewController.swift

package stage;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.Collections;

import exchange.CrowdQExchangeTag;

public class SettingsFragment extends Fragment {

    private MainActivity main;

    private TextView modeLabel;
    private EditText information;
    private Switch sensorSwitch;
    private SeekBar rangeSlider;
    private TextView sliderLabel;
    private Button optionsButton;
    private Spinner hubSegmentedControl;

    public float gravityScale = 1.0f;

    private String showTitle;
    private AlertDialog alert;

    private boolean active = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ScheduledExecutorService scheduler;


    public void setMain(MainActivity main) {
        this.main = main;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        modeLabel            = view.findViewById(R.id.modeLabel);
        information          = view.findViewById(R.id.information);
        sensorSwitch         = view.findViewById(R.id.sensorSwitch);
        rangeSlider          = view.findViewById(R.id.rangeSlider);
        sliderLabel          = view.findViewById(R.id.sliderLabel);
        optionsButton        = view.findViewById(R.id.optionsButton);
        hubSegmentedControl  = view.findViewById(R.id.hubSegmentedControl);

        // Equivalent to setupMenu(["free/demo.json"], overwrite: false)
        setupMenu(Collections.singletonList("free/demo.json"), false);

        // Equivalent to hubSegmentedControl.addTarget hubChanged
        hubSegmentedControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                hubChanged(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Equivalent to sensorSwitch.addTarget sensorSwitchChanged
        sensorSwitch.setOnCheckedChangeListener((buttonView, isOn) -> sensorSwitchChanged(isOn));

        // Equivalent to rangeSlider.addTarget sliderChanged
        rangeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Map 0-200 to 0.0-2.0
                gravityScale = progress / 100.0f;
                String value = String.format("%.2f", gravityScale);
                mainHandler.post(() -> sliderLabel.setText(value + "G"));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Periodically broadcasting show name
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            if (showTitle != null && main != null) {
                String name = showTitle.length() > 5
                        ? showTitle.substring(0, showTitle.length() - 5)
                        : showTitle;
                main.enqueue(CrowdQExchangeTag.LOAD, 0, name);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void setupMenu(List<String> choices, boolean overwrite) {
        if (!overwrite && active) return;
        active = true;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                choices
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        optionsButton.setOnClickListener(v -> {
            // Show choices as AlertDialog — equivalent to UIMenu
            String[] items = choices.toArray(new String[0]);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Choices")
                    .setItems(items, (dialog, which) -> {
                        showTitle = choices.get(which);
                        mainHandler.post(() -> {
                            information.setText("loading " + showTitle);
                            optionsButton.setText(showTitle);
                        });
                        loadShow(showTitle);
                    })
                    .show();
        });
    }

    // Equivalent to getBlueDog call inside setupMenu action
    private void loadShow(String title) {
        executor.execute(() -> {
            try {
                URL url = new URL("https://luxcedia.icu/shows/" + title);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                int status = conn.getResponseCode();

                if (status == 200) {
                    mainHandler.post(() -> information.setText("loaded " + title));
                } else {
                    mainHandler.post(() -> information.setText("Could not load " + title + " with code " + status));
                }
            } catch (Exception e) {
                mainHandler.post(() -> information.setText("Error: " + e.getMessage()));
            }
        });
    }


    public void onVerified() {
        mainHandler.post(() -> {
            modeLabel.setText("Free show mode");
            information.setText("Searching available shows");
        });

        String email = main != null ? main.getEmail() : "";
        String deviceUUID = android.provider.Settings.Secure.getString(
                requireActivity().getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
        );

        executor.execute(() -> {
            try {
                URL url = new URL("https://luxcedia.icu/cgi-bin/freeshows");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = "id=" + email + "&uuid=" + deviceUUID;
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes());
                os.flush();

                int status = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);

                if (status == 200) {
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<String> shows = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        shows.add(jsonArray.getString(i));
                    }
                    mainHandler.post(() -> {
                        information.setText("Found " + shows.size() + " available shows");
                        setupMenu(shows, true);
                    });
                } else {
                    mainHandler.post(() -> information.setText("Bad fetch: " + status));
                }
            } catch (Exception e) {
                mainHandler.post(() -> information.setText("Error: " + e.getMessage()));
            }
        });
    }

    private void popup(String message) {
        mainHandler.post(() -> {
            if (alert != null) alert.dismiss();
            alert = new AlertDialog.Builder(requireContext())
                    .setMessage(message)
                    .setPositiveButton("Dismiss", null)
                    .show();
        });
    }

    private void hubChanged(int selectedIndex) {
        if (selectedIndex != 0) {
            popup("hubs are disabled in this early version");
            mainHandler.post(() -> sensorSwitch.setChecked(false));
        }
    }

    private void sensorSwitchChanged(boolean isOn) {
        if (isOn) {
            int selectedIndex = hubSegmentedControl.getSelectedItemPosition();
            if (selectedIndex == 0) {
                if (main != null) main.setListening(true);
                mainHandler.post(() -> information.setText("Listening for basic sensors"));
            } else {
                popup("the selected hub is handling sensors");
                if (main != null) main.setListening(false);
                mainHandler.post(() -> sensorSwitch.setChecked(false));
            }
        } else {
            if (main != null) main.setListening(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        executor.shutdown();
    }
}