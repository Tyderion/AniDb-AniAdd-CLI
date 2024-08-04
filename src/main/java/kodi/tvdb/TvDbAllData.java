package kodi.tvdb;

import kodi.nfo.Episode;
import kodi.nfo.Series;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class TvDbAllData {
    public String plot;
    public List<TvDbArtworksResponse.Artwork> artworks;
    @Singular
    public List<TvDbEpisodesResponse.Episode> episodes;
    public List<TvDbSeasonResponse.Season> seasons;

    public Series.SeriesBuilder updateSeries(Series.SeriesBuilder builder) {
        // TODO
        return builder;
    }

    public Episode.EpisodeBuilder updateEpisode(Episode.EpisodeBuilder builder) {
        // TODO
        return builder;
    }
}
