package kodi.tmdb;

import kodi.common.UniqueId;
import kodi.nfo.model.Artwork;
import kodi.nfo.model.Movie;
import kodi.nfo.model.Rating;
import kodi.tmdb.requests.TmDbMovieDetailsResponse;
import kodi.tmdb.requests.TmDbMovieImagesResponse;
import kodi.tmdb.requests.TmDbMovieVideosResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Value
@Builder()
public class MovieData {
    private final static String artUrl = "https://image.tmdb.org/t/p/original";

    int id;
    String title;
    String originalTitle;
    String plot;
    int voteCount;
    double voteAverage;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<TmDbMovieVideosResponse.Video> trailer;
    List<TmDbMovieImagesResponse.Image> backdrops;
    List<TmDbMovieImagesResponse.Image> posters;

    public void updateMovie(Movie.MovieBuilder builder) {
        builder.uniqueId(UniqueId.TmDbId(id));
        trailer.ifPresent(trailer -> builder.trailer(STR."https://www.youtube.com/watch?v=\{trailer.getKey()}"));
        builder.title(title)
                .originalTitle(title)
                .plot(plot)
                .rating(Rating.builder().name("tmdb").max(10).rating(voteAverage).voteCount(voteCount).build());

        backdrops.stream().sorted(Comparator.comparingDouble(TmDbMovieImagesResponse.Image::getVoteAverage)
                        .thenComparingInt(TmDbMovieImagesResponse.Image::getVoteCount))
                .limit(40)
                .forEach(backdrop -> builder.fanart(Artwork.builder()
                        .url(STR."\{artUrl}\{backdrop.getFilePath()}")
                        .type(Artwork.ArtworkType.MOVIE_BACKGROUND)
                        .build()));

        posters.stream().sorted(Comparator.comparingDouble(TmDbMovieImagesResponse.Image::getVoteAverage)
                        .thenComparingInt(TmDbMovieImagesResponse.Image::getVoteCount))
                .limit(10)
                .forEach(poster -> builder.fanart(Artwork.builder()
                        .url(STR."\{artUrl}\{poster.getFilePath()}")
                        .type(Artwork.ArtworkType.MOVIE_POSTER)
                        .build()));

    }

    @Slf4j
    public static class MovieDataBuilder {
        private boolean trailersFailed = false;
        private boolean imageFailed = false;
        private boolean detailsFailed = false;

        public boolean isComplete() {
            return trailersFinished() && imagesFinished() && detailsFinished();
        }

        private boolean imagesFinished() {
            return this.posters != null || this.imageFailed;
        }

        private boolean trailersFinished() {
            //noinspection OptionalAssignedToNull
            return this.trailer != null || this.trailersFailed;
        }

        private boolean detailsFinished() {
            return this.plot != null || this.detailsFailed;
        }

        public MovieDataBuilder videos(TmDbMovieVideosResponse videos) {
            if (videos == null) {
                log.trace(STR."No videos found for movie \{id}");
                this.trailersFailed = true;
                this.trailer = Optional.empty();
                return this;
            }
            this.trailer = videos.getEnglishTrailer();
            log.trace(STR."Found \{videos.getVideos().size()} videos for movie \{id}, found english trailer: \{trailer.isPresent()}");
            return this;
        }

        public MovieDataBuilder images(TmDbMovieImagesResponse images) {
            if (images == null) {
                log.trace(STR."No images found for movie \{id}");
                this.imageFailed = true;
                this.posters(List.of());
                this.backdrops(List.of());
                return this;
            }
            log.trace(STR."Found images for movie \{id}: \{images.getPosters().size()} posters, \{images.getBackdrops().size()} backdrops");
            this.posters(images.getPosters());
            this.backdrops(images.getBackdrops());
            return this;
        }

        public MovieDataBuilder details(TmDbMovieDetailsResponse details) {
            if (details == null) {
                log.trace(STR."No details found for movie \{id}");
                this.detailsFailed = true;
                return this;
            }
            log.trace(STR."Found details for movie \{id}: \{details.getTitle()}");
            return this
                    .title(details.getTitle())
                    .originalTitle(details.getOriginalTitle())
                    .plot(details.getPlot())
                    .voteCount(details.getVoteCount())
                    .voteAverage(details.getVoteAverage());
        }
    }

}
