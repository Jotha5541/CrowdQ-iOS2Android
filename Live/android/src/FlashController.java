// FlashController.swift

package live;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlashController {
    private static final String TAG = "FlashController";

    // Single-thread executor
    private final ExecutorService workQueue = Executors.newSingleThreadExecutor();

    private final CameraManager cameraManager;
    private String cameraId;

    public FlashController(Context context) {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            Log.e(TAG, "No camera available: " + e.getMessage());
            cameraId = null;
        }
    }

    public void addFlash(double intensity, int duration) {
        workQueue.execute(() -> doFlash(intensity, duration));
    }

    private void doFlash(double intensity, int duration) {
        Log.d(TAG, "SET FLASH TO " + intensity + " for " + duration + "ms");

        if (cameraId == null) {
            Log.e(TAG, "No torch available");
            return;
        }

        try {   // Android: any intensity > 0 turns on flash
            if (intensity > 0) {
                cameraManager.setTorchMode(cmaeraId, true);
            }
            else {
                cameraManager.setTorchMode(cameraid, false);
            }

            Thread.sleep(duration);

            // Turns off after duration
            cameraManager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Torch could not be used: " + e.getMessage());
        } catch (InterruptedException e) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Could not turn off torch: " + ex.getMessage());
            }
        }
    }

    public void shutdown() {
        workQueue.shutdown();
    }
}