package live;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuzzController {
    /* Single-thread executor for queue */
    private final ExecutorService workQueue = Executors.newSingleThreadExecutor();
    private final Context context;

    public BuzzController(Context context) {
        this.context = context;
    }

    public void addBuzz(int pattern, int duration) {
        workQueue.execute(() -> doBuzz(pattern, duration));
    }

    private void doBuzz(int pattern, int duration) {
        // Equivalent to UIImpactFeedbackGenerator patterns
        long[] timings;
        int[] amplitudes;

        switch(pattern) {
            case 0: // No haptic
                return;
            case 1: // Light
                timings = new long[]{0, 50};
                amplitudes = new int[]{0, 80};
                break;
            case 2: // Medium
                timings = new long[]{0, 80};
                amplitudes = new int[]{0, 150};
                break;
            case 3: // Heavy
                timings = new long[]{0, 100};
                amplitudes = new int[]{0, 255};
                break;
            case 4: // Soft
                timings = new long[]{0, 40};
                amplitudes = new int[]{0, 60};
                break;
            case 5: // Rigid
                timings = new long[]{0, 30};
                amplitudes = new int[]{0, 220};
                break;
            default: // Heavy fallback
                timings = new long[]{0, 100};
                amplitudes = new int[]{0, 255};
                break;
        }

        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager.getDefaultVibrator();
        }
        else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, -1);
            vibrator.vibrate(effect);
        }

        // Equivalent to Thread.sleep(interval);
        try {
            Thread.sleep(duration);
        } catch (InterruptedExecution e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        workQueue.shutdown();
    }
}