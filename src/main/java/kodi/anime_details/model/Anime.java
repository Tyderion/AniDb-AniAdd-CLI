package kodi.anime_details.model;

import jakarta.persistence.*;
import kodi.common.UniqueId;
import kodi.nfo.Actor;
import kodi.nfo.Series;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    @Column(columnDefinition = "DATE")
    private LocalDate startDate;

    @Column(columnDefinition = "DATE")
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

    @OneToMany(mappedBy = "anime", fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    Set<AnimeCreator> creators;

    @OneToMany(mappedBy = "anime", fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    Set<AnimeTag> tags;

    @ToString.Exclude
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "anime_characters",
            joinColumns = @JoinColumn(name = "anime_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "character_id", referencedColumnName = "id"))
    Set<Character> characters;

    @OneToMany(fetch = FetchType.EAGER)
    Set<Episode> episodes;

    @ToString.Exclude
    String description;
    String picture;

    public Series.SeriesBuilder toSeries() {
        val actors = new ArrayList<Actor>();
        val mainCharacters = characters.stream().filter(c -> c.getRole() == Character.Role.MAIN).toList();
        for (int i = 0; i < mainCharacters.size(); i++) {
            actors.add(mainCharacters.get(i).toActor(i));
        }
        val secondaryCharacters = characters.stream().filter(c -> c.getRole() == Character.Role.SECONDARY && c.getSeiyuu() != null).toList();
        for (int i = 0; i < secondaryCharacters.size(); i++) {
            actors.add(secondaryCharacters.get(i).toActor(i + mainCharacters.size()));
        }
        return Series.builder()
                .title(titles.stream().filter(t -> t.getLanguage().equals("en")).findFirst().orElse(titles.iterator().next()).getTitle())
                .originalTitle(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle())
                .voteCount(ratings.stream().filter(r -> r.getType() == Rating.Type.PERMANENT).findFirst().orElse(Rating.builder().count(0).build()).getCount())
                .rating(ratings.stream().filter(r -> r.getType() == Rating.Type.PERMANENT).findFirst().orElse(Rating.builder().rating(0).build()).getRating())
                .plot(description)
                .watched(false)
                .uniqueId(UniqueId.AniDbAnimeId(id))
                .genres(tags.stream().sorted(Comparator.comparingInt(AnimeTag::getWeight).reversed()).limit(5).map(t -> t.getTag().getName()).toList())
                .premiered(startDate)
                .year(startDate.getYear())
                .studio(creators.stream().filter(c -> c.getType() == AnimeCreator.Type.ANIMATION_WORK).map(c -> c.getCreator().getName()).findFirst().orElse(""))
                .actors(actors);
    }


    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Title {
        private String title;
        private String language;
        private String type;
    }

    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Rating {
        private Type type;
        private int count;
        private double rating;

        public enum Type {
            REVIEW, TEMPORARY, PERMANENT;
        }
    }
}
