package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeTag {
    @EmbeddedId
    AnimeTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("animeId")
    @JoinColumn
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Anime anime;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("tagId")
    @JoinColumn
    @EqualsAndHashCode.Exclude
    Tag tag;

    int weight;

    @Builder
    @Embeddable
    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class AnimeTagId implements Serializable {
        int animeId;
        int tagId;
    }
}
