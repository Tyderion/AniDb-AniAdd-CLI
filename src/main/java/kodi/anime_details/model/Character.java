package kodi.anime_details.model;

import jakarta.persistence.*;
import kodi.nfo.Actor;
import kodi.nfo.Series;
import lombok.*;

import java.util.Set;

@Entity
@Builder
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Character {
    @Id
    @Column(nullable = false)
    int id;

    String name;
    Rating rating;
    Role role;
    String gender;
    String picture;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.EAGER, targetEntity = Creator.class)
    Creator seiyuu;

    public Actor toActor(int order) {
        if (seiyuu == null) {
            return null;
        }
        return Actor.builder()
                .name(seiyuu.getName())
                .role(getName())
                .thumb(STR."http://img7.anidb.net/pics/anime/\{seiyuu.getPicture()}")
                .order(order)
                .build();
    }

    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Rating {
        private int count;
        private double rating;
    }

    public enum Role {
        MAIN, SECONDARY, APPEARS_IN;
    }
}
