package aniAdd;

import aniAdd.config.AniConfiguration;

import java.util.logging.Logger;

import aniAdd.misc.ICallBack;
import lombok.Getter;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import fileprocessor.FileProcessor;
import processing.Mod_EpProcessing;
import udpapi2.UdpApi;

public class AniAdd implements IAniAdd {
    @NotNull
    @Getter
    private final AniConfiguration configuration;
    @NotNull
    private final UdpApi api;
    @NotNull
    private final FileProcessor fileProcessor;
    @NotNull
    private final Mod_EpProcessing processing;
    @NotNull
    private final ICallBack<Void> onShutdown;
    private final Logger logger = Logger.getLogger(AniAdd.class.getName());

    public AniAdd(@NotNull AniConfiguration configuration, @NotNull UdpApi api, boolean exitOnTermination, @NotNull FileProcessor fileProcessor, @NotNull Mod_EpProcessing processing, @NotNull ICallBack<Void> onShutdown) {
        this.configuration = configuration;
        this.api = api;
        this.onShutdown = onShutdown;
        this.fileProcessor = fileProcessor;
        this.fileProcessor.AddCallback(event -> {
            if (event == FileProcessor.EventType.NothingToProcess) {
                if (exitOnTermination) {
                    logger.info("File processing nothing to process");
                    Stop();
                }
            } else {
                logger.info(STR."File processing \{event}");
            }
        });
        this.processing = processing;
        this.processing.addListener(event -> {
            if (event == Mod_EpProcessing.ProcessingEvent.Done) {
                logger.info("File moving done");
                if (exitOnTermination) {
                    logger.info("Shutting down");
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
    public void MarkFileAsWatched(String path) {
        val config = getConfiguration().toBuilder()
                .addToMylist(true)
                .renameFiles(false)
                .enableFileMove(false)
                .enableFileRenaming(false)
                .setWatched(true)
                .overwriteMLEntries(true)
                .build();
        fileProcessor.AddFile(path, config);
    }

    public void Stop() {
        logger.info("Terminate AniAdd");
        processing.Terminate();
        api.queueShutdown(_ -> onShutdown.invoke(null));
    }
}
