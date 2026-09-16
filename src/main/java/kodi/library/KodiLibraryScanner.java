package kodi.library;

import aniAdd.kodi.KodiRpcClient;
import aniAdd.kodi.jsonrpc.GetSources;
import aniAdd.kodi.jsonrpc.ScanVideoLibrary;
import com.google.gson.JsonObject;
import config.blocks.KodiConfig;
import config.blocks.KodiLibraryScanConfig;
import kodi.library.LibraryScanPlanner.ResolvedLibrary;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.FileInfo;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Once a directory scan run has finished, asks Kodi to scan the library directories its files landed in. Scans run one after another on a single thread: each waits for Kodi's OnScanFinished before the next
 * starts, so scans requested by overlapping runs queue up instead of colliding.
 */
@Slf4j
public class KodiLibraryScanner {
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long CALL_TIMEOUT_SECONDS = 30;

    private final Supplier<KodiConfig> kodiConfig;
    /** Changed files not yet handed to a scan: filled by a finished run, refilled when a scan fails */
    private final Set<Path> changedFiles = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> lastScan = CompletableFuture.completedFuture(null);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        val thread = new Thread(runnable, "kodi-library-scan");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * @param kodiConfig read on every scan, so host and port overrides applied after construction are honoured
     */
    public KodiLibraryScanner(Supplier<KodiConfig> kodiConfig) {
        this.kodiConfig = kodiConfig;
    }

    public synchronized void onScanRunFinished(List<FileInfo> run) {
        run.stream().filter(FileInfo::isLibraryChanged).map(FileInfo::getFinalFilePath).forEach(changedFiles::add);
        if (changedFiles.isEmpty()) {
            log.info(STR."Scan run with \{run.size()} file(s) changed nothing in the kodi library, not scanning");
            return;
        }
        val batch = new ArrayList<Path>();
        for (val file : changedFiles) {
            if (changedFiles.remove(file)) {
                batch.add(file);
            }
        }
        lastScan = CompletableFuture.runAsync(() -> scan(batch), executor)
                .exceptionally(e -> {
                    // Put the files back so the next run retries them; a watch run would otherwise lose them for good.
                    changedFiles.addAll(batch);
                    log.error(STR."Kodi library scan failed for \{batch.size()} changed file(s), retrying after the next scan run", e);
                    return null;
                });
    }

    /**
     * @return completes once the most recently requested kodi scan is over. Never completes exceptionally.
     */
    public synchronized CompletableFuture<Void> lastScan() {
        return lastScan;
    }

    private void scan(List<Path> batch) {
        val config = kodiConfig.get();
        val scanConfig = config.libraryScan();
        val uri = URI.create(STR."ws://\{config.host()}:\{config.port()}/jsonrpc");
        log.info(STR."Asking kodi at \{uri} to scan its library for \{batch.size()} changed file(s)");

        val client = new KodiRpcClient(uri);
        try {
            client.open().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            val libraries = resolveLibraries(client, scanConfig.libraries());
            val directories = LibraryScanPlanner.plan(scanConfig.scope(), libraries, batch);
            if (directories.isEmpty()) {
                log.info("No changed file lies in a configured kodi library, nothing to scan");
                return;
            }
            for (val directory : directories) {
                scanDirectory(client, directory, scanConfig);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.close();
        }
    }

    private List<ResolvedLibrary> resolveLibraries(KodiRpcClient client, List<KodiLibraryScanConfig.Library> configured) throws Exception {
        Map<String, String> sourcesByLabel = null;
        val resolved = new ArrayList<ResolvedLibrary>();
        for (val library : configured) {
            if (library.path() != null && !library.path().isBlank()) {
                resolved.add(new ResolvedLibrary(library.path(), library.localPath()));
                continue;
            }
            if (sourcesByLabel == null) {
                sourcesByLabel = loadVideoSources(client);
            }
            val path = sourcesByLabel.get(library.name().trim().toLowerCase(Locale.ROOT));
            if (path == null) {
                log.error(STR."Kodi has no video source named \{library.describe()}, skipping it. Known sources: \{sourcesByLabel.keySet()}");
                continue;
            }
            log.debug(STR."Kodi source \{library.describe()} resolved to \{path}");
            resolved.add(new ResolvedLibrary(path, library.localPath()));
        }
        return resolved;
    }

    private Map<String, String> loadVideoSources(KodiRpcClient client) throws Exception {
        val result = client.call(new GetSources()).get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        val sources = new HashMap<String, String>();
        if (!result.has("sources")) {
            return sources;
        }
        for (val element : result.getAsJsonArray("sources")) {
            val source = element.getAsJsonObject();
            val label = source.get("label").getAsString().trim().toLowerCase(Locale.ROOT);
            val previous = sources.putIfAbsent(label, source.get("file").getAsString());
            if (previous != null) {
                log.warn(STR."Kodi has several video sources labelled '\{label}', using \{previous}");
            }
        }
        return sources;
    }

    private void scanDirectory(KodiRpcClient client, String directory, KodiLibraryScanConfig scanConfig) throws Exception {
        CompletableFuture<JsonObject> finished = client.nextNotification(ScanVideoLibrary.ON_SCAN_FINISHED);
        log.info(STR."Scanning kodi directory \{directory}");
        client.call(new ScanVideoLibrary(directory, scanConfig.showDialogs())).get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            finished.get(scanConfig.timeoutInMinutes(), TimeUnit.MINUTES);
            log.info(STR."Kodi finished scanning \{directory}");
        } catch (TimeoutException e) {
            log.warn(STR."Kodi did not report the scan of \{directory} as finished within \{scanConfig.timeoutInMinutes()} minutes, moving on");
        }
    }
}
