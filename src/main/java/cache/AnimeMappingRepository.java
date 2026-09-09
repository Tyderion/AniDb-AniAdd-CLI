package cache;

import cache.entities.AnimeMappingXml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AnimeMappingRepository implements IAnimeMappingRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<AnimeMappingXml> getAnimeMappingXml(String url) {
        try (val session = sessionFactory.openSession()) {
            try {
                log.debug(STR."loading AnimeMappingXml for url: '\{url}'");
                val result = session.get(AnimeMappingXml.class, url);
                log.debug(STR."loaded AnimeMappingXml for url: '\{url}': \{result != null}");
                return Optional.ofNullable(result);
            } catch (GenericJDBCException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean saveAnimeMappingXml(AnimeMappingXml animeMappingXml) {
        try (val session = sessionFactory.openSession()) {
            log.debug(STR."Saving AnimeMappingXml for url: '\{animeMappingXml.getUrl()}'");
            val transaction = session.beginTransaction();
            session.merge(animeMappingXml);
            transaction.commit();
            return true;
        } catch (GenericJDBCException e) {
            log.info(STR."Failed to save AnimeMappingXml for url: '\{animeMappingXml.getUrl()}' because of \{e}");
            return false;
        }
    }
}
