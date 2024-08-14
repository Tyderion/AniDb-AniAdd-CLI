package cache;

import cache.entities.AniDBFileData;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;
import processing.tagsystem.TagSystemTags;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AniDBFileRepository implements IAniDBFileRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<AniDBFileData> getAniDBFileData(String ed2k, long size) {
        try (val session = sessionFactory.openSession()) {
            try {
                log.debug(STR."loading AniDBFileData for ed2k: '\{ed2k}' and size: '\{size}'");
                val result = session.get(AniDBFileData.class, new AniDBFileData.AniDBFileId(ed2k, size));
                log.debug(STR."loaded AniDBFileData: \{result} for ed2k: '\{ed2k}' and size: '\{size}'");
                if (result == null) {
                    return Optional.empty();
                }
                return Optional.of(result);

            } catch (GenericJDBCException e) {
                return Optional.empty();
            }
        }
    }

    private Optional<AniDBFileData> get(AniDBFileData data) {
        return getAniDBFileData(data.getEd2k(), data.getSize());
    }

    @Override
    public Optional<AniDBFileData> getByFilename(@NonNull String filename) {
        try (val session = sessionFactory.openSession()) {
            try {
                log.debug(STR."loading AniDBFileData for filename: '\{filename}'");
                val query = session.createQuery("from AniDBFileData where fileName = :filename", AniDBFileData.class);
                query.setParameter("filename", filename);
                val result = query.uniqueResult();
                log.debug(STR."loaded AniDBFileData: \{result} for filename: '\{filename}'");
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
    public Optional<AniDBFileData> getByFileId(int fileId) {
        try (val session = sessionFactory.openSession()) {
            try {
                log.debug(STR."loading AniDBFileData for fileId: '\{fileId}'");
                val query = session.createQuery("from AniDBFileData as data where :fileId in elements(data.tags)", AniDBFileData.class);
                query.setParameter("fileId", fileId);
                val results = query.getResultList();
                log.debug(STR."loaded AniDBFileData: \{results} for fileId: '\{fileId}'");
                return results.stream().filter(ele -> ele.getTags().get(TagSystemTags.FileId).equals(String.valueOf(fileId))).findFirst();

            } catch (GenericJDBCException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean saveAniDBFileData(AniDBFileData aniDBFileData) {
        try (val session = sessionFactory.openSession()) {
            log.debug(STR."Saving AniDBFileData: \{aniDBFileData}");
            val transaction = session.beginTransaction();
            session.merge(aniDBFileData);
            transaction.commit();
            return true;
        } catch (GenericJDBCException e) {
            log.info(STR."Failed to save AniDBFileData: \{aniDBFileData} because of \{e}");
            return false;
        }
    }

}
