package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Anime {
    @Id
    @Column(nullable = false)
    private int id;

    String type;
    int episodeCount;
    @Column(name = "local_date", columnDefinition = "DATE")
    private LocalDate startDate;

    @Column(name = "local_date", columnDefinition = "DATE")
    private LocalDate endDate;

    @Singular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(joinColumns = {
            @JoinColumn(name = "anime_id", nullable = false, referencedColumnName = "id")})
    private Set<Title> titles;

    @Singular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(joinColumns = {
            @JoinColumn(name = "anime_id", nullable = false, referencedColumnName = "id")})
    private List<Rating> ratings;


    @OneToMany(mappedBy = "creator", fetch = FetchType.EAGER)
    Set<AnimeCreator> creators;


    @OneToMany(mappedBy = "tag", fetch = FetchType.EAGER)
    Set<AnimeTag> tags;

    @ToString.Exclude
    @ManyToMany
    @JoinTable(
            name = "anime_characters",
            joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "character_id", referencedColumnName = "id"))
    Set<Character> characters;

    String description;
    String picture;


    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    public static class Title {
        private String title;
        private String language;
        private String type;
    }

    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    public static class Rating {
        private Type type;
        private int count;
        private double rating;

        public enum Type {
            REVIEW, TEMPORARY, PERMANENT;
        }
    }
}
