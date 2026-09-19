package transcode;

import cache.IAniDBFileRepository;
import cache.IFileHashMappingRepository;
import cache.ITranscodeJobRepository;
import cache.entities.AniDBFileData;
import cache.entities.TranscodeJob;
import cache.entities.TranscodeJob.Phase;
import config.blocks.FileConfig;
import config.blocks.TranscodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import processing.FileInfo;
import processing.FileParser;
import processing.FileRenamer;
import processing.IFileHandler;
import processing.tagsystem.TagSystemTags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Works through the transcode queue, one job at a time, in its own process.
 * <p>
 * Every job walks the phases of {@link TranscodeJob}, and the phase is committed before the step that
 * changes the disk, so a job picked up after a crash continues where it stopped: an interrupted encode
 * starts over, but a verified and hashed file is never encoded again, and a swapped file is only renamed.
 * The order inside a job protects the release's identity: the converted file is hashed and its mapping to
 * the original stored before the original is moved or deleted.
 * <p>
 * Nothing here talks to AniDB. The names come from the cached file data the pipeline stored when it
 * identified the file, overridden by what the converted file actually is (CRC, codec).
 */
@Slf4j
@RequiredArgsConstructor
public class TranscodeRunner {
    static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration LEASE_RENEWAL = Duration.ofMinutes(1);
    private static final String TEMP_SUFFIX = ".transcoding.mkv";
    private static final String OUTPUT_EXTENSION = ".mkv";

    private final TranscodeConfig config;
    private final FileConfig fileConfig;
    private final ITranscodeJobRepository jobs;
    private final IFileHashMappingRepository hashMappings;
    private final IAniDBFileRepository fileRepository;
    private final Transcoder transcoder;
    private final MediaProber prober;
    private final FileRenamer fileRenamer;
    private final IFileHandler fileHandler;

    /**
     * Runs until the thread is interrupted, polling the queue whenever it is empty.
     */
    public void run() throws InterruptedException {
        log.info(STR."Transcoder started, checking the queue every \{config.pollSeconds()}s");
        while (!Thread.currentThread().isInterrupted()) {
            if (!runOnce()) {
                Thread.sleep(Duration.ofSeconds(config.pollSeconds()));
            }
        }
    }

    /**
     * Claims one job and takes it as far as it goes.
     *
     * @return false when there was nothing to claim
     */
    public boolean runOnce() throws InterruptedException {
        val claimed = jobs.claimNext(LEASE);
        if (claimed.isEmpty()) {
            return false;
        }
        val job = claimed.get();
        try (val heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            val thread = new Thread(runnable, "transcode-lease");
            thread.setDaemon(true);
            return thread;
        })) {
            heartbeat.scheduleAtFixedRate(() -> renewLease(job), LEASE_RENEWAL.toMillis(), LEASE_RENEWAL.toMillis(), TimeUnit.MILLISECONDS);
            process(job);
            heartbeat.shutdownNow();
        }
        return true;
    }

    private void renewLease(TranscodeJob job) {
        try {
            jobs.renewLease(job.getEd2k(), job.getSize(), LEASE);
        } catch (Exception e) {
            log.warn(STR."Could not renew the lease on \{job.getSourcePath()}: \{e.getMessage()}");
        }
    }

    private void process(TranscodeJob job) throws InterruptedException {
        log.info(STR."Working on \{job.getSourcePath()} (\{job.getPhase()}, attempt \{job.getAttempts() + 1} of \{config.maxAttempts()})");
        try {
            if ((job.getPhase() == Phase.PENDING || job.getPhase() == Phase.ENCODING) && !encode(job)) {
                return;
            }
            if (job.getPhase() == Phase.ENCODED && !swap(job)) {
                return;
            }
            if (job.getPhase() == Phase.SWAPPED) {
                finish(job);
            }
        } catch (InterruptedException e) {
            // Stopped from outside, not the job's fault: keep the phase and free the job for the next start.
            log.info(STR."Stopped while working on \{job.getSourcePath()}, it resumes on the next start");
            job.setLeaseUntil(null);
            jobs.save(job);
            throw e;
        } catch (Exception e) {
            log.error(STR."Transcode job for \{job.getSourcePath()} failed unexpectedly", e);
            fail(job, STR."Unexpected error: \{e}", false);
        }
    }

    /**
     * PENDING/ENCODING -> ENCODED: encode next to the source, verify, hash, store the mapping, decide the name.
     */
    private boolean encode(TranscodeJob job) throws Exception {
        val source = Path.of(job.getSourcePath());
        if (!Files.isRegularFile(source)) {
            return fail(job, STR."Source \{source} does not exist anymore", true);
        }
        val sourceInfo = prober.probe(source);
        if (sourceInfo.isEmpty()) {
            return fail(job, STR."ffprobe could not read \{source}", false);
        }
        if (!Transcoder.matches(config.match(), sourceInfo.get(), source)) {
            log.info(STR."\{source} no longer matches transcode.match (\{sourceInfo.get().videoCodec()}), skipping it");
            job.setPhase(Phase.SKIPPED);
            job.setLastError(STR."No longer matches transcode.match: \{sourceInfo.get().videoCodec()} \{sourceInfo.get().videoProfile()}");
            job.setLeaseUntil(null);
            jobs.save(job);
            return false;
        }
        val fileData = fileRepository.getAniDBFileData(job.getEd2k(), job.getSize());
        if (fileData.isEmpty()) {
            return fail(job, "No cached AniDB data for this release, so the converted file cannot be named. Scan the file again to refresh it.", true);
        }

        val temp = source.resolveSibling(STR.".\{baseName(source)}\{TEMP_SUFFIX}");
        Files.deleteIfExists(temp);
        job.setPhase(Phase.ENCODING);
        job.setTempPath(temp.toString());
        jobs.save(job);

        val convertedInfo = transcoder.encode(source, sourceInfo.get(), temp);
        if (convertedInfo.isEmpty()) {
            return fail(job, "Encode or verification failed, see the log for details", false);
        }
        val hash = hash(temp);
        if (hash.isEmpty()) {
            return fail(job, STR."Could not hash \{temp}", false);
        }
        val localSize = Files.size(temp);
        val target = targetFor(source, fileData.get(), hash.get()[1], convertedInfo.get().videoCodec());
        if (target.isEmpty()) {
            return fail(job, "The tag system produced no name for the converted file", true);
        }
        if (target.get().equals(source) && transcoder.keepsOriginal()) {
            return fail(job, STR."The converted file would be named \{source.getFileName()} like its original, which transcode.original.mode none keeps. Put the CRC or codec into the tag system's file name.", true);
        }
        if (!target.get().equals(source) && Files.exists(target.get())) {
            return fail(job, STR."\{target.get()} already exists", true);
        }
        if (!hashMappings.save(hash.get()[0], localSize, job.getEd2k(), job.getSize(), source.getFileName().toString())) {
            return fail(job, "Could not store the hash mapping", false);
        }
        job.setLocalEd2k(hash.get()[0]);
        job.setLocalSize(localSize);
        job.setLocalCrc32(hash.get()[1]);
        job.setConvertedPath(target.get().toString());
        job.setPhase(Phase.ENCODED);
        jobs.save(job);
        return true;
    }

    /**
     * ENCODED -> SWAPPED: handle the original, then move the converted file to its name. Safe to repeat
     * after a crash at any point, since each step checks what is already on disk.
     */
    private boolean swap(TranscodeJob job) {
        val source = Path.of(job.getSourcePath());
        val temp = Path.of(job.getTempPath());
        val target = Path.of(job.getConvertedPath());
        if (Files.exists(temp)) {
            if (Files.exists(source) && !transcoder.keepsOriginal() && !transcoder.handleOriginal(source)) {
                return fail(job, STR."Could not handle the original \{source} per transcode.original", false);
            }
            if (!fileHandler.renameFile(temp, target)) {
                return fail(job, STR."Could not move \{temp} to \{target}", false);
            }
        } else if (!Files.exists(target)) {
            return fail(job, STR."The converted file is gone: neither \{temp} nor \{target} exists", true);
        }
        job.setPhase(Phase.SWAPPED);
        jobs.save(job);
        return true;
    }

    /**
     * SWAPPED -> DONE: related files follow the new name, the NFO gets the new codec, and the cached file
     * data carries the new name so the Kodi watcher still finds the file.
     */
    private void finish(TranscodeJob job) {
        val source = Path.of(job.getSourcePath());
        val target = Path.of(job.getConvertedPath());
        if (fileConfig.rename().related()) {
            fileRenamer.renameRelatedFiles(source.getParent(), source.getFileName().toString(), target);
        }
        if (config.nfo() == TranscodeConfig.Nfo.PATCH) {
            prober.probe(target).ifPresent(info -> NfoPatcher.patchVideoCodec(
                    target.resolveSibling(STR."\{baseName(target)}.nfo"), CodecNames.toAniDbVideoCodec(info.videoCodec())));
        }
        fileRepository.getAniDBFileData(job.getEd2k(), job.getSize()).ifPresent(data -> {
            data.setFileName(target.getFileName().toString());
            data.setFolderName(target.getParent().getFileName().toString());
            fileRepository.saveAniDBFileData(data);
        });
        job.setPhase(Phase.DONE);
        job.setLastError(null);
        job.setLeaseUntil(null);
        jobs.save(job);
        log.info(STR."Transcode of \{source.getFileName()} done: \{target}");
    }

    /**
     * What the rename config calls the converted file, in the source's folder: AniDB's data for the release,
     * with CRC and codec taken from the converted file.
     */
    private Optional<Path> targetFor(Path source, AniDBFileData fileData, String crc32, String videoCodec) throws Exception {
        val fileInfo = new FileInfo(source.toFile(), 0, null, fileConfig);
        fileInfo.getData().putAll(fileData.getTags());
        fileInfo.setIdentity(fileData.getEd2k(), fileData.getSize());
        fileInfo.getData().put(TagSystemTags.FileCrc, crc32);
        val codec = CodecNames.toAniDbVideoCodec(videoCodec);
        if (codec != null) {
            fileInfo.getData().put(TagSystemTags.FileVideoCodec, codec);
        }
        // The output is always Matroska, whatever the source's container was.
        fileInfo.setCurrentFile(source.resolveSibling(baseName(source) + OUTPUT_EXTENSION));
        return fileRenamer.targetPath(fileInfo, true);
    }

    /**
     * @return ed2k hash and CRC32, or empty when the file could not be read
     */
    private Optional<String[]> hash(Path file) throws InterruptedException {
        val result = new AtomicReference<String[]>();
        new FileParser(file.toFile(), 0, (_, ed2k, crc32) -> {
            if (ed2k != null) {
                result.set(new String[]{ed2k, crc32});
            }
        }, () -> Thread.currentThread().isInterrupted()).run();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted while hashing");
        }
        return Optional.ofNullable(result.get());
    }

    /**
     * Records the failure. An attempt that fails before anything was committed goes back to PENDING and
     * cleans up its temp file; later phases stay where they are, since their files are the only copy.
     *
     * @return always false, so callers can return it directly
     */
    private boolean fail(TranscodeJob job, String message, boolean permanent) {
        log.error(STR."Transcode job for \{job.getSourcePath()} failed: \{message}");
        if ((job.getPhase() == Phase.PENDING || job.getPhase() == Phase.ENCODING) && job.getTempPath() != null
                && Files.exists(Path.of(job.getSourcePath()))) {
            try {
                Files.deleteIfExists(Path.of(job.getTempPath()));
            } catch (Exception e) {
                log.warn(STR."Could not delete \{job.getTempPath()}: \{e.getMessage()}");
            }
        }
        job.setAttempts(job.getAttempts() + 1);
        job.setLastError(message);
        if (permanent || job.getAttempts() >= config.maxAttempts()) {
            job.setPhase(Phase.FAILED);
        } else if (job.getPhase() == Phase.ENCODING) {
            job.setPhase(Phase.PENDING);
        }
        job.setLeaseUntil(null);
        jobs.save(job);
        return false;
    }

    private static String baseName(Path file) {
        val name = file.getFileName().toString();
        val dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
