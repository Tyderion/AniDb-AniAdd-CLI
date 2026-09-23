package cache;

import cache.entities.AnimeXml;

import java.util.Optional;

public interface IAnimeXmlRepository {
    Optional<AnimeXml> getAnimeXml(long animeId);

    boolean saveAnimeXml(AnimeXml animeXml);
}
