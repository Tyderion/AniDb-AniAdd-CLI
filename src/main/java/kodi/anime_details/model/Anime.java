package kodi.anime_details.model;

import kodi.common.UniqueId;
import kodi.nfo.Actor;
import kodi.nfo.Series;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Anime {
    private static final int maxTags = 25;
    private int id;

    String type;
    int episodeCount;
    private LocalDate startDate;
    private LocalDate endDate;

    private Set<Title> titles;
    private Set<Rating> ratings;

    Set<Creator> creators;
    Set<Tag> tags;

    Set<Character> characters;
    Set<Episode> episodes;

    @ToString.Exclude
    String description;
    String picture;

    public void updateEpisode(kodi.nfo.Episode.EpisodeBuilder builder, int anidbEpisodeNumber) {
        episodes.stream().filter(e -> e.getId() == anidbEpisodeNumber).findFirst()
                .ifPresent(episode -> builder.voteCount(episode.getVoteCount()).rating(episode.getRating()));
        getTags().forEach(builder::genre);
        creators.stream().filter(c -> c.getType() == Creator.Type.DIRECTION).map(Creator::getName).findFirst().ifPresent(builder::director);
        creators.stream().filter(c -> c.getType() == Creator.Type.CHARACTER_DESIGNER).map(Creator::getName).findFirst().ifPresent(builder::credit);
        creators.stream().filter(c -> c.getType() == Creator.Type.ORIGINAL_WORK).map(Creator::getName).findFirst().ifPresent(builder::credit);
    }

    public Series.SeriesBuilder toSeries() {
        val actors = new ArrayList<Actor>();
        var order = 0;
        val mainCharacters = characters.stream().filter(c -> c.getRole() == Character.Role.MAIN).toList();
        for (Character character : mainCharacters) {
            actors.add(character.toActor(++order));
        }
        val secondaryCharacters = characters.stream().filter(c -> c.getRole() == Character.Role.SECONDARY && c.getSeiyuu() != null).toList();
        for (Character character : secondaryCharacters) {
            actors.add(character.toActor(++order));
        }
        val rest = characters.stream().filter(c -> c.getRole() == Character.Role.APPEARS_IN && c.getSeiyuu() != null).toList();
        for (Character character : rest) {
            actors.add(character.toActor(++order));
        }
        return Series.builder()
                .title(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle())
                .originalTitle(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle())
                .voteCount(ratings.stream().filter(r -> r.getType() == Rating.Type.PERMANENT).findFirst().orElse(Rating.builder().count(0).build()).getCount())
                .rating(ratings.stream().filter(r -> r.getType() == Rating.Type.PERMANENT).findFirst().orElse(Rating.builder().rating(0).build()).getRating())
                .artwork(Series.Artwork.builder().url(STR."http://img7.anidb.net/pics/anime/\{picture}").type(Series.ArtworkType.SERIES_POSTER).build())
                .plot(description)
                .uniqueId(UniqueId.AniDbAnimeId(id))
                .genres(getTags().toList())
                .premiered(startDate)
                .year(startDate.getYear())
                .studio(creators.stream().filter(c -> c.getType() == Creator.Type.ANIMATION_WORK).map(Creator::getName).findFirst().orElse(""))
                .actors(actors);
    }

    private Stream<String> getTags() {
        return tags.stream().sorted(Comparator.comparingInt(Tag::getWeight).reversed().thenComparing(Tag::getName)).map(Tag::getName).limit(maxTags);
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Title {
        private String title;
        private String language;
        private String type;
    }

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
