package kodi.tvdb;

import kodi.nfo.Artwork;
import kodi.nfo.Episode;
import kodi.nfo.Series;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.val;

import java.util.List;
import java.util.stream.Stream;

@Builder
@Value
public class TVSeriesData {
    String plot;

    @Singular
    List<Artwork> fanarts;
    @Singular
    List<Artwork> nonFanarts;

    @Singular
    List<TvDbEpisodesResponse.Episode> allEpisodes;
    int seriesId;
    String seriesName;
    TvDbSeasonResponse.SeriesStatus.Status status;
    public static class TVSeriesDataBuilder {
        private boolean episodesFinished = false;

        public boolean isComplete() {
            return this.plot != null && this.episodesFinished && this.nonFanarts != null && this.fanarts != null && this.status != null;
        }

        public TVSeriesDataBuilder description(TvDbDescriptionResponse response) {
            if (response == null) {
                plot("");
                seriesName("");
                return this;
            }
            seriesName(response.getName());
            plot(response.getPlot());
            return this;
        }

        public TVSeriesDataBuilder episodes(TvDbEpisodesResponse response, boolean finished) {
            if (response == null) {
                allEpisodes(List.of());
                episodesFinished = true;
                return this;
            }
            episodesFinished = finished;
            response.getEpisodes().forEach(this::allEpisode);
            return this;
        }

        public TVSeriesDataBuilder seasons(TvDbSeasonResponse response) {
            if (response == null) {
                allEpisodes(List.of());
                status(TvDbSeasonResponse.SeriesStatus.Status.UNKNOWN);
                return this;
            }
            response.getSeasons()
                    .stream().filter(season -> season.getType().getType() == TvDbSeasonResponse.SeasonType.Type.OFFICIAL && season.getSeasonPoster() != null)
                    .map(season -> Artwork.builder()
                            .url(season.getSeasonPoster())
                            .season(season.getNumber())
                            .type(Artwork.ArtworkType.SEASON_POSTER)
                            .build())
                    .forEach(this::nonFanart);

            this.status(response.getSeriesStatus().getStatus());
            return this;
        }

        public TVSeriesDataBuilder artworks(TvDbArtworksResponse response) {
            if (response == null) {
                fanarts(List.of());
                if (nonFanarts == null) {
                    nonFanarts(List.of());
                }
                return this;
            }
            this.seriesId(response.getId());
            val relevantArtworks = response.getArtworks().stream().filter(
                            a -> isRelevantArtworkType(a.getType()))
                    .map(fanart -> Artwork.builder()
                            .url(fanart.getImage())
                            .type(mapType(fanart.getType()))
                            .build()).toList();

            relevantArtworks.stream().filter(a -> a.getUrl().contains("fanart")).forEach(this::fanart);
            relevantArtworks.stream().filter(a -> !a.getUrl().contains("fanart")).forEach(this::nonFanart);
            return this;
        }

        private boolean isRelevantArtworkType(TvDbArtworksResponse.Artwork.Type a) {
            return a == TvDbArtworksResponse.Artwork.Type.SERIES_POSTER ||
                    a == TvDbArtworksResponse.Artwork.Type.SERIES_BANNER ||
                    a == TvDbArtworksResponse.Artwork.Type.SEASON_POSTER ||
                    a == TvDbArtworksResponse.Artwork.Type.SEASON_BANNER ||
                    a == TvDbArtworksResponse.Artwork.Type.SERIES_BACKGROUND ||
                    a == TvDbArtworksResponse.Artwork.Type.SEASON_BACKGROUND ||
                    a == TvDbArtworksResponse.Artwork.Type.SERIES_CLEARART ||
                    a == TvDbArtworksResponse.Artwork.Type.SERIES_CLEARLOGO;
        }

        private Artwork.ArtworkType mapType(TvDbArtworksResponse.Artwork.Type type) {
            return switch (type) {
                case SERIES_BANNER -> Artwork.ArtworkType.SERIES_BANNER;
                case SERIES_POSTER -> Artwork.ArtworkType.SERIES_POSTER;
                case SERIES_BACKGROUND -> Artwork.ArtworkType.SERIES_BACKGROUND;
                case SEASON_BANNER -> Artwork.ArtworkType.SEASON_BANNER;
                case SEASON_POSTER -> Artwork.ArtworkType.SEASON_POSTER;
                case SEASON_BACKGROUND -> Artwork.ArtworkType.SEASON_BACKGROUND;
                case SERIES_CLEARART -> Artwork.ArtworkType.CLEARART;
                case SERIES_CLEARLOGO -> Artwork.ArtworkType.CLEARLOGO;
                default -> throw new IllegalArgumentException(STR."Unknown type: \{type}");
            };
        }

    }


    public Series.SeriesBuilder updateSeries(Series.SeriesBuilder builder) {
        builder.plot(plot);
        builder.fanarts(this.fanarts);

        builder.artworks(this.nonFanarts);

        switch (status) {
            case CONTINUING -> builder.status("Continuing");
            case ENDED -> builder.status("Ended");
            case UPCOMING -> builder.status("Upcoming");
        }

        return builder;
    }

    public void updateEpisode(Episode.EpisodeBuilder builder, int seasonNumber, int episodeNumber) {
        // TODO
        if (seasonNumber == 0) {
            // Specials often cannot be mapped correctly to tvdb
            return;
        }
        val episode = allEpisodes.stream().filter(e -> e.getSeasonNumber() == seasonNumber && e.getNumber() == episodeNumber).findFirst().orElseThrow();
        builder.plot(episode.getPlot());
        builder.thumbnail(STR."https://artworks.thetvdb.com\{episode.getImage()}");
    }
}
