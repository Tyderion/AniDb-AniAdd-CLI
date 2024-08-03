package kodi.tvdb;

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
}
