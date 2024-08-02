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
    Anime anime;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("creatorId")
    @JoinColumn
    Creator creator;

    String type;


    @Builder
    @Embeddable
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class AnimeCreatorKey implements Serializable {
        int animeId;
        int creatorId;
    }
}
