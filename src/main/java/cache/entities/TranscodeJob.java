package cache.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * One queued conversion, keyed by the release AniDB knows (the original ed2k hash and size), so a
 * release is never queued twice.
 * <p>
 * The phase is committed before every step that changes the disk, which is what makes a restart safe:
 * each phase says exactly which files exist and what is left to do.
 * <ul>
 *     <li>PENDING: nothing happened yet. sourcePath is the file to convert.</li>
 *     <li>ENCODING: ffmpeg writes tempPath. A job found here after a crash starts over.</li>
 *     <li>ENCODED: tempPath is verified and hashed, the hash mapping is stored, convertedPath is decided.
 *     The source may or may not have been handled yet.</li>
 *     <li>SWAPPED: the source is handled and the converted file sits at convertedPath. Related files and
 *     metadata may still carry the old name.</li>
 *     <li>DONE, SKIPPED, FAILED: terminal.</li>
 * </ul>
 * Timestamps are epoch millis rather than date types, so lease comparisons in queries do not depend on
 * how the sqlite dialect stores dates.
 */
@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TranscodeJob.TranscodeJobId.class)
public class TranscodeJob {

    public enum Phase {
        PENDING, ENCODING, ENCODED, SWAPPED, DONE, SKIPPED, FAILED;

        public boolean isTerminal() {
            return this == DONE || this == SKIPPED || this == FAILED;
        }
    }

    @Id
    @Column(nullable = false)
    @NonNull
    private String ed2k;

    @Id
    @Column(nullable = false)
    private long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NonNull
    private Phase phase;

    @Column(nullable = false)
    @NonNull
    private String sourcePath;

    private String tempPath;
    private String convertedPath;

    private String localEd2k;
    private Long localSize;
    private String localCrc32;

    private int attempts;

    @Column(length = 4000)
    private String lastError;

    /**
     * A runner holds a job while this lies in the future and renews it while it works. An expired lease
     * means the runner died, so another one may pick the job up where the phase says.
     */
    private Long leaseUntil;

    private long createdAt;
    private long updatedAt;

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class TranscodeJobId {
        private String ed2k;
        private long size;
    }
}
