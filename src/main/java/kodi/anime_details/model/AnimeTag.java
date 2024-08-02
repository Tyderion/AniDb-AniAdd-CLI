package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AnimeTag.AnimeTagId.class)
public class AnimeTag {

    @Id
    int animeId;
    @Id
    int tagId;

    public AnimeTagId getId() {
        return new AnimeTagId(animeId, tagId);
    }

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "animeId", insertable = false, updatable = false, referencedColumnName = "id")
    Anime anime;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "tagId", insertable = false, updatable = false, referencedColumnName = "id")
    Tag tag;

    int weight;

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class AnimeTagId {
        private int animeId;
        private int tagId;
    }
}
