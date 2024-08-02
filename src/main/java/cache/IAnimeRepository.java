package cache;

import cache.entities.AniDBFileData;
import kodi.anime_details.model.Anime;
import lombok.NonNull;

import java.util.Optional;

public interface IAnimeRepository {
    Optional<Anime> getByAnimeId(int animeId);

    boolean saveAnime(Anime anime);
}
