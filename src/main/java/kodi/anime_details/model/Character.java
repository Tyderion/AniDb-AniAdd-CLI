package kodi.anime_details.model;

import kodi.nfo.Actor;
import lombok.*;

@Builder
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Character {
    int id;

    String name;
    Rating rating;
    Role role;
    String gender;
    String picture;

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
