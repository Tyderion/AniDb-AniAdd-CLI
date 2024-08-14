package kodi.anime_details.model;

import kodi.common.UniqueId;
import kodi.nfo.*;
import lombok.*;
import udpapi.reply.ReplyStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Anime {
    private static final int maxTags = 25;
    private int id;

    Type type;
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
                .ifPresent(episode -> createRating(episode.getVoteCount(), episode.getRating(), "anidb"));
        getTags().forEach(builder::genre);
        creators.stream().filter(c -> c.getType() == Creator.Type.DIRECTION).map(Creator::getName).findFirst().ifPresent(builder::director);
        creators.stream().filter(c -> c.getType() == Creator.Type.CHARACTER_DESIGNER).map(Creator::getName).findFirst().ifPresent(builder::credit);
        creators.stream().filter(c -> c.getType() == Creator.Type.ORIGINAL_WORK).map(Creator::getName).findFirst().ifPresent(builder::credit);
    }

    public void updateMovie(Movie.MovieBuilder builder) {
        builder
                .title(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle())
                .originalTitle(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle());
        getTags().forEach(builder::genre);
        creators.stream().filter(c -> c.getType() == Creator.Type.DIRECTION).map(Creator::getName).findFirst().ifPresent(builder::director);
        creators.stream().filter(c -> c.getType() == Creator.Type.CHARACTER_DESIGNER).map(Creator::getName).findFirst().ifPresent(builder::credit);
        creators.stream().filter(c -> c.getType() == Creator.Type.ORIGINAL_WORK).map(Creator::getName).findFirst().ifPresent(builder::credit);
        getNfoRatings().forEach(builder::rating);

        builder.thumbnail(STR."http://img7.anidb.net/pics/anime/\{picture}")
                .plot(description)
                .uniqueId(UniqueId.AniDbAnimeId(id))
                .genres(getTags().toList())
                .premiered(startDate)
                .studio(creators.stream().filter(c -> c.getType() == Creator.Type.ANIMATION_WORK).map(Creator::getName).findFirst().orElse(""))
                .actors(getActors());
    }

    public Series.SeriesBuilder toSeries() {
        val actors = getActors();
        val series = Series.builder()
                .title(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle())
                .originalTitle(titles.stream().filter(t -> t.getType().equals("main")).findFirst().orElse(titles.iterator().next()).getTitle());
        getNfoRatings().forEach(series::rating);

        return series.artwork(Artwork.builder().url(STR."http://img7.anidb.net/pics/anime/\{picture}").type(Artwork.ArtworkType.SERIES_POSTER).build())
                .plot(description)
                .uniqueId(UniqueId.AniDbAnimeId(id))
                .genres(getTags().toList())
                .premiered(startDate)
                .year(startDate.getYear())
                .studio(creators.stream().filter(c -> c.getType() == Creator.Type.ANIMATION_WORK).map(Creator::getName).findFirst().orElse(""))
                .actors(actors);
    }

    private Stream<kodi.nfo.Rating> getNfoRatings() {
        return ratings.stream()
                .sorted((r1, r2) -> {
                    if (r1.getType() == Rating.Type.PERMANENT) {
                        return -1;
                    }
                    if (r2.getType() == Rating.Type.PERMANENT) {
                        return 1;
                    }
                    return 0;
                }).map(rating -> {
                    val name = switch (rating.getType()) {
                        case REVIEW -> "anidb_review";
                        case TEMPORARY -> "anidb_temporary";
                        case PERMANENT -> "anidb";
                    };
                    return createRating(rating.getCount(), rating.getRating(), name);
                });
    }

    private kodi.nfo.Rating createRating(int voteCount, double rating, String name) {
        return kodi.nfo.Rating.builder()
                .voteCount(voteCount)
                .rating(rating)
                .max(10)
                .name(name)
                .build();
    }

    private List<Actor> getActors() {
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

        return actors;
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

    @RequiredArgsConstructor
    public enum Type {
        TV_Series("TV Series"), OVA("OVA"), MOVIE("Movie"), OTHER("Other"), WEB("Web"), TV_SPECIAL("TV Special"), MUSIC_VIDEO("Music Video");

        private final String value;

        public static Type fromString(String value) {
            for (Type status : Type.values()) {
                if (String.valueOf(status.value).equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }
}
