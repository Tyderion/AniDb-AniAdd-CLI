package cache;

import cache.entities.TranscodeJob;
import config.blocks.TranscodeConfig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface ITranscodeJobRepository {
    /**
     * Queues the release for conversion. An unfinished job only gets its source path updated; a finished,
     * skipped or failed one is left alone unless existing is RETRANSCODE, which resets it.
     *
     * @return true when a job is now pending for this release
     */
    boolean enqueue(String ed2k, long size, Path sourcePath, TranscodeConfig.Existing existing);

    /**
     * Atomically takes the next unfinished job nobody holds, preferring jobs that are already past encoding
     * so their work is not lost, and holds it for the lease duration.
     */
    Optional<TranscodeJob> claimNext(Duration lease);

    void renewLease(String ed2k, long size, Duration lease);

    /**
     * Stores the job as given. Callers set the phase before touching the disk for the next step.
     */
    void save(TranscodeJob job);

    Optional<TranscodeJob> get(String ed2k, long size);
}
