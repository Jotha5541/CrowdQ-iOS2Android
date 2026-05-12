package threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SerialExecutor {
    private final ExecutorService executor;

    public SerialExecutor(String tag) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, tag);
            t.setDaemon(true);
            return t;
        });
    }

    public void execute(Runnable command) {
        executor.execute(command);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}