package aniAdd;

import aniAdd.config.AniConfiguration;

import aniAdd.misc.ICallBack;
import lombok.Getter;
import lombok.extern.java.Log;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import fileprocessor.FileProcessor;
import processing.Mod_EpProcessing;
import udpapi.UdpApi;

@Log
public class AniAdd implements IAniAdd {
    @NotNull @Getter private final AniConfiguration configuration;
    @NotNull private final UdpApi api;
    @NotNull private final FileProcessor fileProcessor;
    @NotNull private final Mod_EpProcessing processing;
    @NotNull private final ICallBack<Void> onShutdown;

    public AniAdd(@NotNull AniConfiguration configuration, @NotNull UdpApi api, boolean exitOnTermination, @NotNull FileProcessor fileProcessor, @NotNull Mod_EpProcessing processing, @NotNull ICallBack<Void> onShutdown) {
        this.configuration = configuration;
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
                log.fine(STR."File processing \{event}");
            }
        });
        this.processing = processing;
        this.processing.addListener(event -> {
            if (event == Mod_EpProcessing.ProcessingEvent.Done) {
                log.info("File moving done");
                if (exitOnTermination) {
                    log.info("Shutting down");
                    Stop();
                }
            }
        });
    }

    @Override
    public void ProcessDirectory(String directory) {
        fileProcessor.Scan(directory);
    }

    @Override
    public void MarkFileAsWatched(@NotNull String path) {
        val config = getConfiguration().toBuilder()
                .addToMylist(true)
                .enableFileMove(false)
                .enableFileRenaming(false)
                .setWatched(true)
                .overwriteMLEntries(true)
                .build();
        fileProcessor.AddFile(path, config);
    }

    public void Stop() {
        log.info("Terminate AniAdd");
        processing.Terminate();
        api.queueShutdown(_ -> onShutdown.invoke(null));
    }
}
