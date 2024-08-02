package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCreator {
    @EmbeddedId
    AnimeCreatorKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("animeId")
    @JoinColumn
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Anime anime;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("creatorId")
    @JoinColumn
    @EqualsAndHashCode.Exclude
    Creator creator;

    String type;


    @Data
    @Builder
    @Embeddable
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class AnimeCreatorKey implements Serializable {
        int animeId;
        int creatorId;
    }
}
