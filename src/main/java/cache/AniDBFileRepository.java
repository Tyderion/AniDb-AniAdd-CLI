package cache;

import cache.entities.AniDBFileData;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;

import java.util.Optional;

@Log
@RequiredArgsConstructor
public class AniDBFileRepository implements IAniDBFileRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<AniDBFileData> getAniDBFileData(String ed2k, long size) {
        try (val session = sessionFactory.openSession()) {
            try {
                val result = session.get(AniDBFileData.class, new AniDBFileData.AniDBFileId(ed2k, size));
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
    public Optional<AniDBFileData> getByFilename(@NonNull String filename) {
        try (val session = sessionFactory.openSession()) {
            try {
                val query = session.createQuery("from AniDBFileData where fileName = :filename", AniDBFileData.class);
                query.setParameter("filename", filename);
                val result = query.uniqueResult();
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
    public boolean saveAniDBFileData(AniDBFileData aniDBFileData) {
        try (val session = sessionFactory.openSession()) {
            val transaction = session.beginTransaction();
            session.persist(aniDBFileData);
            transaction.commit();
            return true;
        } catch (GenericJDBCException e) {
            log.info(STR."Failed to save AniDBFileData: \{aniDBFileData} because of \{e}");
            return false;
        }
    }

}
