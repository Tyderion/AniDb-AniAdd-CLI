package kodi.common;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UniqueId {
    Type type;
    long value;

    public static UniqueId AniDbAnimeId(long value) {
        return new UniqueId(Type.ANIDB, value);
    }

    public static UniqueId TvDbId(long value) {
        return new UniqueId(Type.TVDB, value);
    }

    public static UniqueId ImDbId(long value) {
        return new UniqueId(Type.IMDB, value);
    }

    public static UniqueId TmDbId(long value) {
        return new UniqueId(Type.TMDB, value);
    }

    public static UniqueId AniDbFileId(long value) {
        return new UniqueId(Type.ANIDB_FILEID, value);
    }

    @RequiredArgsConstructor
    public enum Type {
        ANIDB("anidb"), TVDB("tvdb"), IMDB("imdb"), TMDB("tmdb"), ANIDB_FILEID("anidb_fid");
        private final String name;
    }

}
