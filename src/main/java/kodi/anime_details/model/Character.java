package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Builder
@Data
@ToString(onlyExplicitlyIncluded = false)
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
    @ManyToMany
    @JoinTable(
            name = "anime_characters",
            joinColumns = @JoinColumn(name = "character_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"))
    Set<Anime> animes;

    @ToString.Exclude
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "character")
    Creator seiyuu;

    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    public static class Rating {
        private int count;
        private double rating;
    }

    public enum Role {
        MAIN, SECONDARY, APPEARS_IN;
    }
}
