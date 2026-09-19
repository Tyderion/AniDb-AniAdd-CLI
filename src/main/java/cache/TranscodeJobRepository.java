package cache;

import cache.entities.TranscodeJob;
import cache.entities.TranscodeJob.Phase;
import config.blocks.TranscodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The transcode queue. The scan/watch process writes to it and the transcoder process takes from it, so
 * everything that decides ownership happens in a single conditional UPDATE: sqlite serialises writers,
 * and a row only changes hands if it still looks the way the claimer saw it.
 */
@Slf4j
@RequiredArgsConstructor
public class TranscodeJobRepository implements ITranscodeJobRepository {
    private static final List<Phase> OPEN = List.of(Phase.PENDING, Phase.ENCODING, Phase.ENCODED, Phase.SWAPPED);
    private static final int CLAIM_CANDIDATES = 5;

    private final SessionFactory sessionFactory;

    @Override
    public boolean enqueue(String ed2k, long size, Path sourcePath, TranscodeConfig.Existing existing) {
        val now = System.currentTimeMillis();
        return sessionFactory.fromTransaction(session -> {
            val job = session.get(TranscodeJob.class, new TranscodeJob.TranscodeJobId(ed2k, size));
            if (job == null) {
                session.persist(TranscodeJob.builder()
                        .ed2k(ed2k)
                        .size(size)
                        .phase(Phase.PENDING)
                        .sourcePath(sourcePath.toString())
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                log.info(STR."Queued \{sourcePath} for transcoding");
                return true;
            }
            if (job.getPhase() == Phase.PENDING) {
                // Not started yet, so following the file to wherever it went since is safe.
                job.setSourcePath(sourcePath.toString());
                job.setUpdatedAt(now);
                return true;
            }
            if (!job.getPhase().isTerminal()) {
                log.info(STR."\{sourcePath} is already being transcoded (\{job.getPhase()})");
                return false;
            }
            if (existing != TranscodeConfig.Existing.RETRANSCODE) {
                log.info(STR."Not queueing \{sourcePath}: its release was already transcoded (\{job.getPhase()}). Set transcode.existing to retranscode to redo it.");
                return false;
            }
            job.setPhase(Phase.PENDING);
            job.setSourcePath(sourcePath.toString());
            job.setTempPath(null);
            job.setConvertedPath(null);
            job.setLocalEd2k(null);
            job.setLocalSize(null);
            job.setLocalCrc32(null);
            job.setAttempts(0);
            job.setLastError(null);
            job.setLeaseUntil(null);
            job.setUpdatedAt(now);
            log.info(STR."Queued \{sourcePath} for transcoding again");
            return true;
        });
    }

    @Override
    public Optional<TranscodeJob> claimNext(Duration lease) {
        val now = System.currentTimeMillis();
        val candidates = sessionFactory.fromSession(session -> session.createSelectionQuery(
                        "from TranscodeJob where phase in (:open) and (leaseUntil is null or leaseUntil < :now) " +
                                "order by case when phase = :pending then 1 else 0 end, createdAt", TranscodeJob.class)
                .setParameter("open", OPEN)
                .setParameter("now", now)
                .setParameter("pending", Phase.PENDING)
                .setMaxResults(CLAIM_CANDIDATES)
                .getResultList());
        for (val candidate : candidates) {
            val claimed = sessionFactory.fromTransaction(session -> session.createMutationQuery(
                            "update TranscodeJob set leaseUntil = :until, updatedAt = :now " +
                                    "where ed2k = :ed2k and size = :size and phase = :phase and (leaseUntil is null or leaseUntil < :now)")
                    .setParameter("until", now + lease.toMillis())
                    .setParameter("now", now)
                    .setParameter("ed2k", candidate.getEd2k())
                    .setParameter("size", candidate.getSize())
                    .setParameter("phase", candidate.getPhase())
                    .executeUpdate());
            if (claimed == 1) {
                return get(candidate.getEd2k(), candidate.getSize());
            }
        }
        return Optional.empty();
    }

    @Override
    public void renewLease(String ed2k, long size, Duration lease) {
        val now = System.currentTimeMillis();
        sessionFactory.inTransaction(session -> session.createMutationQuery(
                        "update TranscodeJob set leaseUntil = :until where ed2k = :ed2k and size = :size and leaseUntil is not null")
                .setParameter("until", now + lease.toMillis())
                .setParameter("ed2k", ed2k)
                .setParameter("size", size)
                .executeUpdate());
    }

    @Override
    public void save(TranscodeJob job) {
        job.setUpdatedAt(System.currentTimeMillis());
        sessionFactory.inTransaction(session -> session.merge(job));
    }

    @Override
    public Optional<TranscodeJob> get(String ed2k, long size) {
        return Optional.ofNullable(sessionFactory.fromSession(session -> session.get(TranscodeJob.class, new TranscodeJob.TranscodeJobId(ed2k, size))));
    }
}
