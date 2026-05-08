// SoundController.swift

package live;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SoundController {

    private static final String TAG = "SoundController";

    // Single-thread executor
    private final ExecutorService workQueue = Executors.newSingleThreadExecutor();

    private final Semaphore semaphore = new Semaphore(1);

    private MediaPlayer audioPlayer;
    private final Context context;

    public SoundController(Context context) {
        this.context = context;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);

        // "Silent" asset to warm up audio
//        warmUpAudio();
    }


    private void warmUpAudio() {
        try {
            audioPlayer = MediaPlayer.create(context, R.raw.silent);
            if (audioPlayer != null) {
                audioPlayer.setLooping(false); // Equivalent to numberOfLoops = 0
                audioPlayer.start();
                Log.d(TAG, "Audio warmed up");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error warming up audio: " + e.getMessage());
        }
    }

    public void addSound(byte[] sound, int duration) {
        workQueue.execute(() -> playSound(sound, duration));
    }

    private void playSound(byte[] sound, int duration) {
        try {
            semaphore.acquire();

            // Write bytes to a temp file — equivalent to AVAudioPlayer(data: sound)
            File tempFile = File.createTempFile("crowdq_sound", ".mp3", context.getCacheDir());
            tempFile.deleteOnExit();

            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(sound);
            fos.close();

            // Release previous player
            if (audioPlayer != null) {
                audioPlayer.release();
                audioPlayer = null;
            }

            audioPlayer = new MediaPlayer();

            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            audioPlayer.setDataSource(tempFile.getAbsolutePath());
            audioPlayer.prepare();

            audioPlayer.setLooping(false); // numberOfLoops = 0
            audioPlayer.start();

            Log.d(TAG, "Playing sound for " + duration + "ms");

            Thread.sleep(duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "SoundController interrupted: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    public void shutdown() {
        workQueue.shutdown();
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
    }
}