
package bluedog;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class BlueDog {
    private static final String TAG = "BlueDog";

    private JSONObject commands;
    private String showTitle;
    private final ShowEffects effects;

    public BlueDog(ShowEffects effects) {
        this.effects = effects;
    }

    public void loadShow(String json) {
        try {
            JSONObject root = new JSONObject(json);
            showTitle = root.optString("title", "Untitled");
            commands = root.optJSONObject("commands");
            Log.d(TAG, "Show loaded: " + showTitle + " - " + (commands != null ? commands.length() : 0) + " commands");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse show JSON: " + e.getMessage());
            commands = null;
        }
    }

    public void executeCommand(String command, int sensorId) {
        if (commands == null) {
            Log.d(TAG, "No show loaded - ignoring: " + command);
            return;
        }

        JSONObject entry = commands.optJSONObject(command + ":" + sensorId);
        if (entry == null) {
            entry = commands.optJSONObject(command);
            Log.d(TAG, "No mapping for: " + command + " sensor=" + sensorId);
            return;
        }

        Log.d(TAG, "Executing: " + command + " sensor=" + sensorId);

        // Flash command
        JSONObject flash = entry.optJSONObject("flash");
        if (flash != null) {
            effects.flash(flash.optDouble("intensity", 0.8), flash.optInt("duration", 300));
        }

        // Buzz command
        JSONObject buzz = entry.optJSONObject("buzz");
        if (buzz != null) {
            effects.buzz(buzz.optInt("pattern", 1), buzz.optInt("duration", 200));
        }

        String soundUrl = entry.optString("sound", null);
        if (!soundUrl.isEmpty()) {
            int soundDuration = entry.optInt("soundDuration", 2000);
            new Thread(() -> {
                try {
                    java.io.InputStream is = new java.net.URL(soundUrl).openStream();
                    byte[] data = is.readAllBytes();
                    is.close();
                    effects.playSound(data, soundDuration);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch sound: " + e.getMessage());
                }
            }).start();
        }

        String message = entry.optString("message", null);
        if (!message.isEmpty()) {
            effects.showMessage(message);
        }

        String videoUrl = entry.optString("video", null);
        if (!videoUrl.isEmpty()) {
            effects.playVideo(videoUrl);
        }

        if (entry.optBoolean("sync", false)) {
            effects.sync();
        }
    }

    public boolean isLoaded() {
        return (commands != null);
    }

    public String getShowTitle() {
        return showTitle;
    }

}