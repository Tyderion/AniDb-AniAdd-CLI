package kodi.common;

import lombok.*;

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

    public static UniqueId AniDbEpisodeId(long value) {
        return new UniqueId(Type.ANIDB_EPISODEID, value);
    }

    @RequiredArgsConstructor
    @Getter
    public enum Type {
        ANIDB("anidb"), TVDB("tvdb"), IMDB("imdb"), TMDB("tmdb"), ANIDB_FILEID("anidb_fid"), ANIDB_EPISODEID("anidb_eid");
        private final String name;
    }

}
