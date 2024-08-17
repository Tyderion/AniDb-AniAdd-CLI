package kodi.tvdb;

import kodi.nfo.model.Artwork;
import kodi.nfo.model.Episode;
import kodi.nfo.model.Series;
import kodi.tvdb.requests.TvDbArtworksResponse;
import kodi.tvdb.requests.TvDbDescriptionResponse;
import kodi.tvdb.requests.TvDbEpisodesResponse;
import kodi.tvdb.requests.TvDbSeasonResponse;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;

@Slf4j
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
                log.trace(STR."No description found for series \{seriesId}");
                plot("");
                seriesName("");
                return this;
            }
            log.trace(STR."Found description for series \{seriesId}: \{response.getName()}");
            seriesName(response.getName());
            plot(response.getPlot());
            return this;
        }

        public TVSeriesDataBuilder episodes(TvDbEpisodesResponse response, boolean finished) {
            if (response == null) {
                log.trace(STR."No episodes found for series \{seriesId}");
                allEpisodes(List.of());
                episodesFinished = true;
                return this;
            }
            log.trace(STR."Found \{response.getEpisodes().size()} episodes for series \{seriesId}");
            episodesFinished = finished;
            response.getEpisodes().forEach(this::allEpisode);
            return this;
        }

        public TVSeriesDataBuilder seasons(TvDbSeasonResponse response) {
            if (response == null) {
                log.trace(STR."No seasons found for series \{seriesId}");
                allEpisodes(List.of());
                status(TvDbSeasonResponse.SeriesStatus.Status.UNKNOWN);
                return this;
            }
            val seasonArtworks = response.getSeasons()
                    .stream().filter(season -> season.getType().getType() == TvDbSeasonResponse.SeasonType.Type.OFFICIAL && season.getSeasonPoster() != null)
                    .map(season -> Artwork.builder()
                            .url(season.getSeasonPoster())
                            .season(season.getNumber())
                            .type(Artwork.ArtworkType.SEASON_POSTER)
                            .build()).toList();

            log.trace(STR."Found \{seasonArtworks.size()} valid seasons posters for series \{seriesId}");
            seasonArtworks.forEach(this::nonFanart);
            this.status(response.getSeriesStatus().getStatus());
            return this;
        }

        public TVSeriesDataBuilder artworks(TvDbArtworksResponse response) {
            if (response == null) {
                log.trace(STR."No artworks found for series \{seriesId}");
                fanarts(List.of());
                if (nonFanarts == null) {
                    nonFanarts(List.of());
                }
                return this;
            }
            val relevantArtworks = response.getArtworks().stream().filter(
                            a -> isRelevantArtworkType(a.getType()))
                    .map(fanart -> Artwork.builder()
                            .url(fanart.getImage())
                            .type(mapType(fanart.getType()))
                            .build()).toList();

            log.trace(STR."Found \{relevantArtworks.size()} relevant artworks for series \{seriesId}");
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
        log.trace(STR."Updating series \{seriesName}");
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
        log.trace(STR."Updating episode \{seasonNumber}x\{episodeNumber} for series \{seriesName}");
        if (seasonNumber == 0) {
            // Specials often cannot be mapped correctly to tvdb
            log.warn(STR."Specials currently are not mapped correctly to TVDB, skipping episode \{episodeNumber} of series \{seriesName}");
            return;
        }
        val episode = allEpisodes.stream().filter(e -> e.getSeasonNumber() == seasonNumber && e.getNumber() == episodeNumber).findFirst().orElseThrow();
        builder.plot(episode.getPlot());
        builder.thumbnail(STR."https://artworks.thetvdb.com\{episode.getImage()}");
    }
}
