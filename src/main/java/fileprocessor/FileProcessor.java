package fileprocessor;

import aniAdd.misc.ICallBack;
import config.CliConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.FileInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
@RequiredArgsConstructor
public class FileProcessor {
    private final Processor processor;
    private final FileInfo.Configuration configuration;
    private final List<ICallBack<EventType>> onEvents = new ArrayList<>();

    private final ExecutorService executorService;

    public void AddFile(Path path) {
        AddFile(path, configuration);
    }

    public void AddCallback(ICallBack<EventType> callback) {
        onEvents.add(callback);
    }

    public void AddFile(Path path, FileInfo.Configuration configuration) {
        if (Files.exists(path)) {
            processor.addFiles(List.of(path.toFile()), configuration);
        }
    }

    public void Scan(Path directory) {
        val findFiles = executorService.submit(new FindFiles(directory));
        try {
            val files = findFiles.get();
            if (files.isEmpty()) {
                log.warn("No files found");
                sendEvent(FileProcessor.EventType.NothingToProcess);
            } else {
                sendEvent(FileProcessor.EventType.Processing);
                processor.addFiles(files);
            }
        } catch (InterruptedException e) {
            log.error(STR."Find Files was interrupted \{e.getMessage()}");
            sendEvent(FileProcessor.EventType.ErrorFindingFiles);
        } catch (ExecutionException e) {
            log.error(STR."Find Files was cancelled \{e.getCause().getMessage()}");
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
        void addFiles(Collection<File> newFiles, FileInfo.Configuration configuration);
    }
}
