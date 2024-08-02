package kodi.anime_details.model;

import jakarta.persistence.*;
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
