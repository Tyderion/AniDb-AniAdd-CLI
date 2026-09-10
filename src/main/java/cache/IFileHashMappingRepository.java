package cache;

import cache.entities.FileHashMapping;

import java.util.Optional;

public interface IFileHashMappingRepository {
    /**
     * @return the mapping for a locally produced file, or empty when this hash and size is not one.
     */
    Optional<FileHashMapping> get(String ed2k, long size);

    /**
     * Records that (ed2k, size) was produced from (sourceEd2k, sourceSize). If the source is itself a
     * converted file, the stored row points at its root original instead, so lookups never chain.
     */
    boolean save(String ed2k, long size, String sourceEd2k, long sourceSize, String originalFileName);
}
