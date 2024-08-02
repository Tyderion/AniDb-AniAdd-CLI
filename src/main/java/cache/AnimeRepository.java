package cache;

import cache.entities.AniDBFileData;
import kodi.anime_details.model.Anime;
import kodi.anime_details.model.AnimeCreator;
import kodi.anime_details.model.Character;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;

import java.util.Optional;

@Log
@RequiredArgsConstructor
public class AnimeRepository implements IAnimeRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<Anime> getByAnimeId(int animeId) {
        try (val session = sessionFactory.openSession()) {
            try {
                val result = session.get(Anime.class, animeId);
                if (result == null) {
                    return Optional.empty();
                }
                return Optional.of(result);

            } catch (GenericJDBCException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean saveAnime(Anime anime) {
        try (val session = sessionFactory.openSession()) {
            var transaction = session.beginTransaction();
            persistIfNotExists(session, anime.getId(), anime);
            anime.getCreators().forEach(ac -> {
                ac.setAnime(anime);
                persistIfNotExists(session, ac.getId(), ac);
                persistIfNotExists(session, ac.getCreator().getId(), ac.getCreator());
            });
            anime.getTags().forEach(at -> {
                at.setAnime(anime);
                persistIfNotExists(session, at.getId(), at);
                persistIfNotExists(session, at.getTag().getId(), at.getTag());
            });
            anime.getCharacters().forEach(character -> {
                if (character.getSeiyuu() != null) {
                    persistIfNotExists(session, character.getSeiyuu().getId(), character.getSeiyuu());
                }
                persistIfNotExists(session, character.getId(), character);
            });
            transaction.commit();
            return true;
        } catch (GenericJDBCException e) {
            log.info(STR."Failed to save AniDBFileData: \{anime} because of \{e}");
            return false;
        }
    }

    private boolean persistIfNotExists(Session session, Object id, Object entity) {
        if (id == null) {
            session.persist(entity);
            return true;
        } else if (session.get(entity.getClass(), id) == null) {
            session.persist(entity);
            return true;
        }
        return false;
    }
}
