package cache;

import cache.entities.AnimeXml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AnimeXmlRepository implements IAnimeXmlRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<AnimeXml> getAnimeXml(long animeId) {
        try (val session = sessionFactory.openSession()) {
            try {
                log.debug(STR."loading AnimeXml for animeId: '\{animeId}'");
                val result = session.get(AnimeXml.class, animeId);
                log.debug(STR."loaded AnimeXml for animeId: '\{animeId}': \{result != null}");
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
    public boolean saveAnimeXml(AnimeXml animeXml) {
        try (val session = sessionFactory.openSession()) {
            log.debug(STR."Saving AnimeXml for animeId: '\{animeXml.getAnimeId()}'");
            val transaction = session.beginTransaction();
            session.merge(animeXml);
            transaction.commit();
            return true;
        } catch (GenericJDBCException e) {
            log.info(STR."Failed to save AnimeXml for animeId: '\{animeXml.getAnimeId()}' because of \{e}");
            return false;
        }
    }

}
