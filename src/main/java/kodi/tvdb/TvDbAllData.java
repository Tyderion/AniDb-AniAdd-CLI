package kodi.tvdb;

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

    public Series.SeriesBuilder updateSeries(Series.SeriesBuilder builder) {
        // TODO
        builder.plot(plot);
        seasons
                .stream().filter(season -> season.getType().getType() == TvDbSeasonResponse.SeasonType.Type.OFFICIAL && season.getSeasonPoster() != null)
                .forEach(season -> {
            builder.artwork(Series.Artwork.builder()
                    .url(season.getSeasonPoster())
                    .season(season.getNumber())
                    .type(Series.ArtworkType.SEASON_POSTER)
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
                builder.fanart(Series.Artwork.builder()
                        .url(a.getImage())
                        .type(mapType(a.getType()))
                        .build()
                );
            } else {
                builder.artwork(Series.Artwork.builder()
                        .url(a.getImage())
                        .type(mapType(a.getType()))
                        .build()
                );
            }


        });

        return builder;
    }

    private Series.ArtworkType mapType(TvDbArtworksResponse.Artwork.Type type) {
        return switch (type) {
            case SERIES_BANNER -> Series.ArtworkType.SERIES_BANNER;
            case SERIES_POSTER -> Series.ArtworkType.SERIES_POSTER;
            case SERIES_BACKGROUND -> Series.ArtworkType.SERIES_BACKGROUND;
            case SEASON_BANNER -> Series.ArtworkType.SEASON_BANNER;
            case SEASON_POSTER -> Series.ArtworkType.SEASON_POSTER;
            case SEASON_BACKGROUND -> Series.ArtworkType.SEASON_BACKGROUND;
            case SERIES_CLEARART -> Series.ArtworkType.CLEARART;
            case SERIES_CLEARLOGO -> Series.ArtworkType.CLEARLOGO;
            default -> throw new IllegalArgumentException(STR."Unknown type: \{type}");
        };
    }

    public Episode.EpisodeBuilder updateEpisode(Episode.EpisodeBuilder builder, int seasonNumber, int episodeNumber) {
        // TODO
        val episode = episodes.stream().filter(e -> e.getSeasonNumber() == seasonNumber && e.getNumber() == episodeNumber).findFirst().orElseThrow();
        builder.plot(episode.getPlot());
        builder.thumbnail(STR."https://artworks.thetvdb.com\{episode.getImage()}");

        return builder;
    }
}
