package cache;

import cache.entities.AniDBFileData;
import lombok.NonNull;

import java.util.Optional;

public interface IAniDBFileRepository {
    Optional<AniDBFileData> getAniDBFileData(String ed2k, long size);

    Optional<AniDBFileData> getByFilename(@NonNull String filename);

    boolean saveAniDBFileData(AniDBFileData aniDBFileData);

    Optional<AniDBFileData> getByFileId(int fileId);
}
