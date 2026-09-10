package cache;

import cache.entities.FileHashMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.SessionFactory;
import org.hibernate.exception.GenericJDBCException;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class FileHashMappingRepository implements IFileHashMappingRepository {
    private final SessionFactory sessionFactory;

    @Override
    public Optional<FileHashMapping> get(String ed2k, long size) {
        if (ed2k == null) {
            return Optional.empty();
        }
        try (val session = sessionFactory.openSession()) {
            try {
                val result = session.get(FileHashMapping.class, new FileHashMapping.FileHashId(ed2k, size));
                if (result == null) {
                    return Optional.empty();
                }
                log.debug(STR."Hash \{ed2k} (\{size} bytes) maps to original \{result.getOriginalEd2k()} (\{result.getOriginalSize()} bytes)");
                return Optional.of(result);
            } catch (GenericJDBCException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean save(String ed2k, long size, String sourceEd2k, long sourceSize, String originalFileName) {
        // Collapse the chain here rather than at read time: if the source was itself converted, this
        // file's original is the source's original.
        val root = get(sourceEd2k, sourceSize);
        val originalEd2k = root.map(FileHashMapping::getOriginalEd2k).orElse(sourceEd2k);
        val originalSize = root.map(FileHashMapping::getOriginalSize).orElse(sourceSize);
        val mapping = FileHashMapping.builder()
                .ed2k(ed2k)
                .size(size)
                .originalEd2k(originalEd2k)
                .originalSize(originalSize)
                .originalFileName(originalFileName)
                .build();
        try (val session = sessionFactory.openSession()) {
            val transaction = session.beginTransaction();
            session.merge(mapping);
            transaction.commit();
            log.info(STR."Stored hash mapping \{ed2k} (\{size}) -> original \{originalEd2k} (\{originalSize})");
            return true;
        } catch (GenericJDBCException e) {
            log.error(STR."Failed to store hash mapping for \{ed2k}: \{e}");
            return false;
        }
    }
}
