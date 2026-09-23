package cache.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeMappingXml {

    // Keyed by source URL so a changed kodi.metadata.animeMappingUrl never serves a stale cache for the wrong source.
    @Id
    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    @NonNull
    private String xml;

    @CreationTimestamp
    @Column(updatable = false)
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime updatedAt;
}
