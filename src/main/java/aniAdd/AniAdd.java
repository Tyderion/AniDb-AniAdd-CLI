package aniAdd;

import aniAdd.misc.ICallBack;
import config.blocks.FileConfig;
import config.blocks.KodiConfig;
import config.blocks.MyListConfig;
import fileprocessor.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import processing.EpisodeProcessing;
import udpapi.UdpApi;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public class AniAdd implements IAniAdd {
    @NotNull private final UdpApi api;
    @NotNull private final FileProcessor fileProcessor;
    @NotNull private final EpisodeProcessing processing;
    @NotNull private final ICallBack<Void> onShutdown;
    @NotNull private final FileConfig fileConfig;
    @NotNull private final KodiConfig kodiConfig;

    public AniAdd(@NotNull UdpApi api, boolean exitOnTermination, @NotNull FileProcessor fileProcessor, @NotNull EpisodeProcessing processing, @NotNull ICallBack<Void> onShutdown, @NotNull FileConfig fileConfig, @NotNull KodiConfig kodiConfig) {
        this(api, exitOnTermination, fileProcessor, processing, onShutdown, () -> CompletableFuture.completedFuture(null), fileConfig, kodiConfig);
    }

    /**
     * @param afterBatch asked whenever processing reports everything done; shutdown on termination waits for the future it returns
     */
    public AniAdd(@NotNull UdpApi api, boolean exitOnTermination, @NotNull FileProcessor fileProcessor, @NotNull EpisodeProcessing processing, @NotNull ICallBack<Void> onShutdown, @NotNull Supplier<CompletableFuture<Void>> afterBatch, @NotNull FileConfig fileConfig, @NotNull KodiConfig kodiConfig) {
        this.api = api;
        this.onShutdown = onShutdown;
        this.fileConfig = fileConfig;
        this.kodiConfig = kodiConfig;
        this.fileProcessor = fileProcessor;
        this.fileProcessor.AddCallback(event -> {
            if (event == FileProcessor.EventType.NothingToProcess) {
                if (exitOnTermination) {
                    log.info("File processing nothing to process");
                    Stop();
                }
            } else {
                log.debug(STR."File processing \{event}");
            }
        });
        this.processing = processing;
        this.processing.addListener(event -> {
            if (event == EpisodeProcessing.ProcessingEvent.Done) {
                log.info("File moving done");
                val afterBatchDone = afterBatch.get();
                if (exitOnTermination) {
                    afterBatchDone.whenComplete((_, _) -> {
                        log.info("Shutting down");
                        Stop();
                    });
                }
            }
        });
    }

    @Override
    public void ProcessDirectory(Path directory) {
        fileProcessor.Scan(directory);
    }

    @Override
    public void MarkFileAsWatched(@NotNull Path path) {
        if (!kodiConfig.markWatched()) {
            log.debug(STR."kodi.markWatched is off, so not writing the watched state of \{path} to MyList");
            return;
        }
        // The watched flag is the event itself, not a setting: Kodi has just told us this was played.
        // Adding and overwriting are how that gets recorded, so they stay on here even when a deployment
        // turns file.mylist.add off to keep scans from touching MyList. Storage type follows the config.
        val config = FileConfig.builder()
                .mylist(MyListConfig.builder()
                        .watched(true)
                        .overwrite(true)
                        .add(true)
                        .storageType(fileConfig.mylist().storageType())
                        .build())
                .build();
        fileProcessor.AddFile(path, config);
    }

    public void Stop() {
        log.info("Terminate AniAdd");
        processing.Terminate();
        api.queueShutdown(_ -> onShutdown.invoke(null));
    }
}
