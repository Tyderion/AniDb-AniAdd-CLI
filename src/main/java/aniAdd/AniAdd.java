package aniAdd;

import aniAdd.misc.ICallBack;
import config.blocks.FileConfig;
import config.blocks.MyListConfig;
import fileprocessor.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import processing.EpisodeProcessing;
import processing.FileInfo;
import udpapi.UdpApi;

import java.nio.file.Path;

@Slf4j
public class AniAdd implements IAniAdd {
    @NotNull private final UdpApi api;
    @NotNull private final FileProcessor fileProcessor;
    @NotNull private final EpisodeProcessing processing;
    @NotNull private final ICallBack<Void> onShutdown;

    public AniAdd(@NotNull UdpApi api, boolean exitOnTermination, @NotNull FileProcessor fileProcessor, @NotNull EpisodeProcessing processing, @NotNull ICallBack<Void> onShutdown) {
        this.api = api;
        this.onShutdown = onShutdown;
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
                if (exitOnTermination) {
                    log.info("Shutting down");
                    Stop();
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
        val config = FileInfo.Configuration.of(
                FileConfig.builder().build(),
                MyListConfig.builder()
                        .watched(true)
                        .overwrite(true)
                        .add(true)
                        .build()

        );
        fileProcessor.AddFile(path, config);
    }

    public void Stop() {
        log.info("Terminate AniAdd");
        processing.Terminate();
        api.queueShutdown(_ -> onShutdown.invoke(null));
    }
}
