package kodi.tmdb;

import lombok.*;

import java.util.List;
import java.util.Optional;

@Value
@Builder()
public class MovieData {

    int id;
    String title;
    String plot;
    int voteCount;
    double voteAverage;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<TmDbMovieVideosResponse.Video> trailer;
    List<TmDbMovieImagesResponse.Image> backdrops;
    List<TmDbMovieImagesResponse.Image> posters;

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
                this.trailersFailed = true;
                this.trailer = Optional.empty();
                return this;
            }
            this.trailer = videos.getEnglishTrailer();
            return this;
        }

        public MovieDataBuilder images(TmDbMovieImagesResponse images) {
            if (images == null) {
                this.imageFailed = true;
                this.posters(List.of());
                this.backdrops(List.of());
                return this;
            }
            this.posters(images.getPosters());
            this.backdrops(images.getBackdrops());
            return this;
        }

        public MovieDataBuilder details(TmDbMovieDetailsResponse details) {
            if (details == null) {
                this.detailsFailed = true;
                return this;
            }
            return this.id(details.getId())
                    .title(details.getTitle())
                    .plot(details.getPlot())
                    .voteCount(details.getVoteCount())
                    .voteAverage(details.getVoteAverage());
        }
    }

}
