package processing;

import lombok.extern.java.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log
public class DoOnFileSystem implements AutoCloseable {
    private final ExecutorService executor;

    public DoOnFileSystem() {
        executor = Executors.newSingleThreadExecutor();
    }

    public void run(Runnable runnable) {
        executor.execute(runnable);
    }

    @Override
    public void close() throws Exception {
        executor.close();
    }
}
