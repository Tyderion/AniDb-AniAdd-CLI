package cache.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Maps a locally produced file back to the file AniDB knows.
 * <p>
 * A re-encode changes both ed2k hash and size, which are exactly the two things the AniDB FILE
 * command identifies a file by. This table is what lets the rest of the pipeline keep treating a
 * converted file as the release it came from: look the current hash and size up here, and use the
 * original pair for anything AniDB related.
 * <p>
 * Rows always point at the root original, never at an intermediate step, so a file that gets
 * converted a second time still resolves in a single lookup.
 */
@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(FileHashMapping.FileHashId.class)
public class FileHashMapping {

    @Id
    @Column(nullable = false)
    @NonNull
    private String ed2k;

    @Id
    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    @NonNull
    private String originalEd2k;

    @Column(nullable = false)
    private long originalSize;

    /**
     * Only for making the table readable by a human; nothing keys off it.
     */
    @Column
    private String originalFileName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class FileHashId {
        private String ed2k;
        private long size;
    }
}
