package fileprocessor;

import aniAdd.config.AniConfiguration;
import aniAdd.misc.ICallBack;
import lombok.*;
import lombok.extern.java.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Log
@RequiredArgsConstructor
public class FileProcessor {
    private final Processor processor;
    private final AniConfiguration configuration;
    private final List<ICallBack<EventType>> onEvents = new ArrayList<>();

    private final ExecutorService executorService;

    public void AddFile(String path) {
        AddFile(path, configuration);
    }

    public void AddCallback(ICallBack<EventType> callback) {
        onEvents.add(callback);
    }

    public void AddFile(String path, AniConfiguration configuration) {
        File file = new File(path);
        if (file.exists()) {
            processor.addFiles(List.of(file), configuration);
        }
    }


    public void Scan(String directory) {
        val findFiles = executorService.submit(new FindFiles(directory));
        try {
            val files = findFiles.get();
            if (files.isEmpty()) {
                log.warning("No files found");
                sendEvent(FileProcessor.EventType.NothingToProcess);
            } else {
                sendEvent(FileProcessor.EventType.Processing);
                processor.addFiles(files);
            }
        } catch (InterruptedException e) {
            log.severe(STR."Find Files was interrupted \{e.getMessage()}");
            sendEvent(FileProcessor.EventType.ErrorFindingFiles);
        } catch (ExecutionException e) {
            log.severe(STR."Find Files was cancelled \{e.getCause().getMessage()}");
            sendEvent(FileProcessor.EventType.ErrorFindingFiles);
            throw new RuntimeException(e);
        }
    }

    private void sendEvent(EventType eventType) {
        onEvents.forEach(cb -> cb.invoke(eventType));
    }

    public enum EventType {
        NothingToProcess, Processing, ErrorFindingFiles
    }

    public interface Processor {
        void addFiles(Collection<File> newFiles);

        void addFiles(Collection<File> newFiles, AniConfiguration configuration);
    }
}
