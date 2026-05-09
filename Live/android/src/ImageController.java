// ImageController.swift

package live;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ImageController {

    private static final String TAG = "ImageController";

    // Single-thread executor
    private final ExecutorService workQueue = Executors.newSingleThreadExecutor();

    private final Semaphore semaphore = new Semaphore(1);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void addImage(FragmentActivity activity, int imageResId, int duration) {
        workQueue.execute(() -> displayImage(activity, imageResId, duration));
    }

    private void displayImage(FragmentActivity activity, int imageResId, int duration) {
        try {
            semaphore.acquire();

            Log.d(TAG, "popup image for " + duration + "ms");

            final ImagePopupFragment[] popup = {null};

            mainHandler.post(() -> {
                popup[0] = ImagePopupFragment.newInstance(imageResId, duration, width, height);
                popup[0].show(activity.getSupportFragmentManager(), "image_popup");
            });

            Thread.sleep(duration);

            mainHandler.post(() -> {
                if (popup[0] != null) {
                    popup[0].dismiss();
                }
                semaphore.release();
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            semaphore.release();
            Log.e(TAG, "ImageController interrupted: " + e.getMessage());
        }
    }

    public void addSyncPoint(Runnable onComplete) {
        workQueue.execute(onComplete);
    }

    public void shutdown() {
        workQueue.shutdown();
    }
}