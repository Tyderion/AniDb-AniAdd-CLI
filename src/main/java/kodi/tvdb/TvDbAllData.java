package kodi.tvdb;

import kodi.nfo.Artwork;
import kodi.nfo.Episode;
import kodi.nfo.Series;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.val;

import java.util.List;

@Builder
@Value
public class TvDbAllData {
    String plot;
    List<TvDbArtworksResponse.Artwork> artworks;
    @Singular
    List<TvDbEpisodesResponse.Episode> episodes;
    List<TvDbSeasonResponse.Season> seasons;
    int seriesId;
    String seriesName;
    TvDbSeasonResponse.SeriesStatus.Status status;

    public Series.SeriesBuilder updateSeries(Series.SeriesBuilder builder) {
        builder.plot(plot);
        seasons
                .stream().filter(season -> season.getType().getType() == TvDbSeasonResponse.SeasonType.Type.OFFICIAL && season.getSeasonPoster() != null)
                .forEach(season -> {
            builder.artwork(Artwork.builder()
                    .url(season.getSeasonPoster())
                    .season(season.getNumber())
                    .type(Artwork.ArtworkType.SEASON_POSTER)
                    .build()
            );
        });

        artworks.stream().filter(
                a -> a.getType() == TvDbArtworksResponse.Artwork.Type.SERIES_POSTER ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SERIES_BANNER ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SEASON_POSTER ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SEASON_BANNER ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SERIES_BACKGROUND ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SEASON_BACKGROUND ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SERIES_CLEARART ||
                        a.getType() == TvDbArtworksResponse.Artwork.Type.SERIES_CLEARLOGO
        ).forEach(a -> {
            if (a.getImage().contains("fanart")) {
                builder.fanart(Artwork.builder()
                        .url(a.getImage())
                        .type(mapType(a.getType()))
                        .build()
                );
            } else {
                builder.artwork(Artwork.builder()
                        .url(a.getImage())
                        .type(mapType(a.getType()))
                        .build()
                );
            }


        });

        switch (status) {
            case CONTINUING -> builder.status("Continuing");
            case ENDED -> builder.status("Ended");
            case UPCOMING -> builder.status("Upcoming");
        }

        return builder;
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

    public void updateEpisode(Episode.EpisodeBuilder builder, int seasonNumber, int episodeNumber) {
        // TODO
        if (seasonNumber == 0) {
            // Specials often cannot be mapped correctly to tvdb
            return;
        }
        val episode = episodes.stream().filter(e -> e.getSeasonNumber() == seasonNumber && e.getNumber() == episodeNumber).findFirst().orElseThrow();
        builder.plot(episode.getPlot());
        builder.thumbnail(STR."https://artworks.thetvdb.com\{episode.getImage()}");
    }
}
