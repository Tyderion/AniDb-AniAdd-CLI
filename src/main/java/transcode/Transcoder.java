package transcode;

import config.blocks.MoveConfig;
import config.blocks.TranscodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.IFileHandler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Re-encodes video into a single configured output format, leaving audio, subtitles, attachments and
 * chapters as they are. Everything here is synchronous: the caller (the transcode runner) owns the
 * thread and the ordering, so it can persist each step before starting the next.
 * <p>
 * Nothing in this class removes a source file on its own. {@link #encode} only ever writes the target,
 * and {@link #handleOriginal} is a separate call the runner makes once the result is safely recorded.
 */
@Slf4j
@RequiredArgsConstructor
public class Transcoder {
    private final TranscodeConfig config;
    private final MediaProber prober;
    private final IFileHandler fileHandler;

    /**
     * Probes the file and decides whether it matches the configured criteria.
     */
    public Optional<MediaInfo> matches(Path file) {
        return prober.probe(file).filter(info -> matches(config.match(), info, file));
    }

    static boolean matches(TranscodeConfig.MatchConfig match, MediaInfo info, Path file) {
        val codec = info.videoCodec();
        if (codec == null) {
            log.debug(STR."No video stream in \{file}, not transcoding");
            return false;
        }
        if (match.videoCodecs().stream().noneMatch(candidate -> candidate.equalsIgnoreCase(codec))) {
            log.debug(STR."\{file} is \{codec}, which is not in transcode.match.videoCodecs, leaving it alone");
            return false;
        }
        val profiles = match.profiles();
        if (profiles != null && !profiles.isEmpty() && profiles.stream().noneMatch(candidate -> candidate.equalsIgnoreCase(info.videoProfile()))) {
            log.debug(STR."\{file} has profile \{info.videoProfile()}, which is not in transcode.match.profiles, leaving it alone");
            return false;
        }
        if (match.minBitrateKbps() != null && info.bitRateKbps() < match.minBitrateKbps()) {
            log.debug(STR."\{file} has \{info.bitRateKbps()} kbps, below transcode.match.minBitrateKbps \{match.minBitrateKbps()}, leaving it alone");
            return false;
        }
        return true;
    }

    /**
     * Encodes source into target and verifies the result. On anything short of a verified file the target
     * is deleted and the result is empty; the source is never touched.
     *
     * @throws InterruptedException when the thread is interrupted, after killing ffmpeg and deleting the target
     */
    public Optional<MediaInfo> encode(Path source, MediaInfo sourceInfo, Path target) throws InterruptedException {
        val command = buildCommand(source, target);
        val ffmpegLog = target.resolveSibling(target.getFileName() + ".log");
        log.info(STR."Transcoding \{source} (\{sourceInfo.videoCodec()}, \{Math.round(sourceInfo.durationSeconds())}s)");
        log.debug(STR."ffmpeg command: \{String.join(" ", command)}");
        Process process = null;
        try {
            val started = System.currentTimeMillis();
            // Output goes to a file rather than a pipe, so waitFor stays interruptible for the whole encode.
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ffmpegLog.toFile()).start();
            if (!process.waitFor(config.timeoutMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                log.error(STR."ffmpeg exceeded transcode.timeoutMinutes (\{config.timeoutMinutes()}) for \{source}");
                deleteQuietly(target);
                return Optional.empty();
            }
            val output = readQuietly(ffmpegLog.toFile());
            if (process.exitValue() != 0) {
                log.error(STR."ffmpeg exited with \{process.exitValue()} for \{source}: \{output}");
                deleteQuietly(target);
                return Optional.empty();
            }
            if (!output.isBlank()) {
                log.debug(STR."ffmpeg output for \{source}: \{output}");
            }
            val minutes = (System.currentTimeMillis() - started) / 60000.0;
            val newInfo = prober.probe(target);
            if (newInfo.isEmpty() || !verify(source, sourceInfo, newInfo.get())) {
                deleteQuietly(target);
                return Optional.empty();
            }
            val ratio = sourceInfo.sizeInBytes() == 0 ? 0 : 100.0 * newInfo.get().sizeInBytes() / sourceInfo.sizeInBytes();
            log.info(STR."Transcoded \{source.getFileName()} to \{newInfo.get().videoCodec()} in \{String.format("%.1f", minutes)} min, \{String.format("%.0f", ratio)}% of the original size");
            return newInfo;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            deleteQuietly(target);
            throw e;
        } catch (Exception e) {
            log.error(STR."Transcode of \{source} failed: \{e.getMessage()}. Is '\{config.ffmpegPath()}' on the PATH?");
            deleteQuietly(target);
            return Optional.empty();
        } finally {
            deleteQuietly(ffmpegLog);
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
        // The target may be a hidden temp name without a meaningful extension, so the container is explicit.
        command.addAll(List.of("-f", "matroska", target.toString()));
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
     * Moves the source aside, deletes it, or leaves it, per transcode.original.
     *
     * @return false when the source is still in place although the config says it should not be
     */
    public boolean handleOriginal(Path source) {
        val handling = config.original();
        switch (handling.mode()) {
            case DELETE -> {
                fileHandler.deleteFile(source);
                return !Files.exists(source);
            }
            case MOVE -> {
                val target = handling.folder().resolve(source.getFileName());
                if (!fileHandler.renameFile(source, target)) {
                    log.error(STR."Could not move the original out of the way: \{source} -> \{target}");
                    return false;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    public boolean keepsOriginal() {
        return config.original().mode() == MoveConfig.HandlingConfig.Mode.NONE;
    }

    private static String readQuietly(File file) {
        try {
            return Files.readString(file.toPath()).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn(STR."Could not delete \{path}: \{e.getMessage()}");
        }
    }
}
