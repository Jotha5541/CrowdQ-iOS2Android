package icu.luxcedia.crowdq.live;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MediaEffectsPipeline {
    // Optimization: removing sleep method and keep threads free for burst BLE packets
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void queueEffect(Runnable executeTask, long sleepDurationMs, Runnable cleanupTasks) {
        executeTask.run();

        scheduler.schedule(() -> {
            try {
                cleanupTask.run();
            }
            catch (Exception e) {
                e.printStackTrace();    // Prevents silent failures
            }
        }, sleepDurationMs, TimeUnit.MILLISECONDS);
    }

    public void destroy() {
        scheduler.shutdownNow();
    }
}