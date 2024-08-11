package processing;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
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
