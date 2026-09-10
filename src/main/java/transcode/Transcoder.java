package transcode;

import config.blocks.MoveConfig;
import config.blocks.TranscodeConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.IFileHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Re-encodes video into a single configured output format, leaving audio, subtitles, attachments and
 * chapters as they are. Encodes run one at a time on their own thread, so a long encode never blocks
 * hashing or the AniDB session.
 * <p>
 * A converted file only replaces its source once it passes verification, and the source is then
 * handled per config. Anything that fails leaves the source untouched, which is the whole point: a
 * failed encode must never cost a file.
 */
@Slf4j
@RequiredArgsConstructor
public class Transcoder implements AutoCloseable {
    private static final String IN_PROGRESS_SUFFIX = ".transcoding.mkv";
    private static final String KEPT_ORIGINAL_SUFFIX = ".transcoded.mkv";

    private final TranscodeConfig config;
    private final MediaProber prober;
    private final IFileHandler fileHandler;
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(runnable -> {
        val thread = new Thread(runnable, "transcoder");
        thread.setDaemon(true);
        return thread;
    });

    @Getter
    @Accessors(fluent = true)
    public static class Result {
        private final Path file;
        private final MediaInfo info;

        public Result(Path file, MediaInfo info) {
            this.file = file;
            this.info = info;
        }
    }

    /**
     * Probes the file and decides whether it matches the configured input formats.
     */
    public Optional<MediaInfo> matches(Path file) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        val info = prober.probe(file);
        if (info.isEmpty()) {
            return Optional.empty();
        }
        val codec = info.get().videoCodec();
        if (codec == null) {
            log.debug(STR."No video stream in \{file}, not transcoding");
            return Optional.empty();
        }
        val matched = config.videoCodecs().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(codec));
        if (!matched) {
            log.debug(STR."\{file} is \{codec}, which is not in transcode.videoCodecs, leaving it alone");
            return Optional.empty();
        }
        return info;
    }

    /**
     * Queues an encode. The callback runs on the encoder thread, with an empty result when anything
     * went wrong; in that case the source file is still exactly where it was.
     */
    public void transcode(Path source, MediaInfo sourceInfo, Consumer<Optional<Result>> onDone) {
        encoder.execute(() -> {
            try {
                onDone.accept(run(source, sourceInfo));
            } catch (Exception e) {
                log.error(STR."Transcode of \{source} failed unexpectedly: \{e.getMessage()}");
                onDone.accept(Optional.empty());
            }
        });
    }

    private Optional<Result> run(Path source, MediaInfo sourceInfo) {
        val temp = source.resolveSibling(baseName(source) + IN_PROGRESS_SUFFIX);
        val command = buildCommand(source, temp);
        log.info(STR."Transcoding \{source} (\{sourceInfo.videoCodec()}, \{Math.round(sourceInfo.durationSeconds())}s)");
        log.debug(STR."ffmpeg command: \{String.join(" ", command)}");
        try {
            val started = System.currentTimeMillis();
            val process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (val stdout = process.getInputStream()) {
                output = new String(stdout.readAllBytes());
            }
            if (!process.waitFor(config.timeoutMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                log.error(STR."ffmpeg exceeded transcode.timeoutMinutes (\{config.timeoutMinutes()}) for \{source}");
                deleteQuietly(temp);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                log.error(STR."ffmpeg exited with \{process.exitValue()} for \{source}: \{output.trim()}");
                deleteQuietly(temp);
                return Optional.empty();
            }
            if (!output.isBlank()) {
                log.debug(STR."ffmpeg output for \{source}: \{output.trim()}");
            }
            val minutes = (System.currentTimeMillis() - started) / 60000.0;
            val newInfo = prober.probe(temp);
            if (newInfo.isEmpty() || !verify(source, sourceInfo, newInfo.get())) {
                deleteQuietly(temp);
                return Optional.empty();
            }
            val target = handleOriginal(source);
            if (target.isEmpty()) {
                deleteQuietly(temp);
                return Optional.empty();
            }
            if (!fileHandler.renameFile(temp, target.get())) {
                log.error(STR."Could not move the converted file into place: \{temp} -> \{target.get()}");
                return Optional.empty();
            }
            val ratio = sourceInfo.sizeInBytes() == 0 ? 0 : 100.0 * newInfo.get().sizeInBytes() / sourceInfo.sizeInBytes();
            log.info(STR."Transcoded \{source.getFileName()} to \{newInfo.get().videoCodec()} in \{String.format("%.1f", minutes)} min, \{String.format("%.0f", ratio)}% of the original size");
            return Optional.of(new Result(target.get(), newInfo.get()));
        } catch (Exception e) {
            log.error(STR."Transcode of \{source} failed: \{e.getMessage()}. Is '\{config.ffmpegPath()}' on the PATH?");
            deleteQuietly(temp);
            return Optional.empty();
        }
    }

    private List<String> buildCommand(Path source, Path target) {
        val command = new ArrayList<String>();
        if (config.nice() != null && config.nice() != 0) {
            command.addAll(List.of("nice", "-n", String.valueOf(config.nice())));
        }
        command.addAll(List.of(config.ffmpegPath(), "-nostdin", "-hide_banner", "-loglevel", "warning", "-y", "-i", source.toString()));
        command.addAll(split(config.streamArgs()));
        command.addAll(split(config.videoArgs()));
        command.add(target.toString());
        return command;
    }

    private static List<String> split(String args) {
        if (args == null || args.isBlank()) {
            return List.of();
        }
        return Arrays.stream(args.trim().split("\\s+")).toList();
    }

    private boolean verify(Path source, MediaInfo before, MediaInfo after) {
        val durationDelta = Math.abs(before.durationSeconds() - after.durationSeconds());
        if (durationDelta > config.durationToleranceSeconds()) {
            log.error(STR."Rejecting transcode of \{source}: duration moved by \{String.format("%.2f", durationDelta)}s (tolerance \{config.durationToleranceSeconds()}s)");
            return false;
        }
        if (before.sizeInBytes() > 0 && after.sizeInBytes() < before.sizeInBytes() * config.minSizeRatio()) {
            log.error(STR."Rejecting transcode of \{source}: output is \{after.sizeInBytes()} bytes, below \{config.minSizeRatio()} of the original \{before.sizeInBytes()}");
            return false;
        }
        if (after.videoStreams() != before.videoStreams()
                || after.audioStreams() != before.audioStreams()
                || after.subtitleStreams() != before.subtitleStreams()
                || after.attachmentStreams() != before.attachmentStreams()) {
            log.error(STR."Rejecting transcode of \{source}: stream counts changed. video \{before.videoStreams()}->\{after.videoStreams()}, audio \{before.audioStreams()}->\{after.audioStreams()}, subtitle \{before.subtitleStreams()}->\{after.subtitleStreams()}, attachment \{before.attachmentStreams()}->\{after.attachmentStreams()}");
            return false;
        }
        return true;
    }

    /**
     * Deals with the source file and answers where the converted file belongs. When the source stays
     * put, the converted file gets its own name so nothing is overwritten.
     */
    private Optional<Path> handleOriginal(Path source) {
        val handling = config.original();
        switch (handling.mode()) {
            case DELETE -> {
                fileHandler.deleteFile(source);
                return Optional.of(source);
            }
            case MOVE -> {
                val target = handling.folder().resolve(source.getFileName());
                if (!fileHandler.renameFile(source, target)) {
                    log.error(STR."Could not move the original out of the way: \{source} -> \{target}");
                    return Optional.empty();
                }
                return Optional.of(source);
            }
            default -> {
                return Optional.of(source.resolveSibling(baseName(source) + KEPT_ORIGINAL_SUFFIX));
            }
        }
    }

    private static String baseName(Path file) {
        val name = file.getFileName().toString();
        val dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn(STR."Could not delete \{path}: \{e.getMessage()}");
        }
    }

    @Override
    public void close() {
        encoder.shutdownNow();
    }
}
