package kodi;

import kodi.anime_details.AnimeDetailsLoader;
import kodi.anime_details.model.Anime;
import kodi.anime_mapping.AnimeMappingLoader;
import kodi.anime_mapping.model.AnimeMapping;
import kodi.nfo.MovieNfoWriter;
import kodi.nfo.SeriesNfoWriter;
import kodi.nfo.model.Artwork;
import kodi.nfo.model.Episode;
import kodi.nfo.model.Movie;
import kodi.nfo.model.Series;
import kodi.tmdb.MovieData;
import kodi.tmdb.TmDbApi;
import kodi.tvdb.TVSeriesData;
import kodi.tvdb.TvDbApi;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;
import utils.http.DownloadHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class KodiMetadataGenerator {
    private static final int MAX_EXPORTED_FANARTS = 10;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<Long, AnimeMapping> animeMapping = initAnimeMapping();
    private final Map<Long, SeriesNfoWriter> nfoGenerators = new HashMap<>();
    private final DownloadHelper downloadHelper;
    private final TvDbApi tvDbApi;
    private final TmDbApi tmDbApi;
    private final String animeMappingUrl;
    private final EnumSet<OverwriteConfiguration> overwriteConfiguration;

    private Map<Long, AnimeMapping> initAnimeMapping() {
        return new AnimeMappingLoader(animeMappingUrl).getAnimeMapping();
    }

    public void generateMetadata(FileInfo fileInfo, OnDone onDone) {
        log.info(STR."Generating metadata for \{fileInfo.getFile().getName()}");
        val aniDbAnimeId = Integer.parseInt(fileInfo.getData().get(TagSystemTags.AnimeId));
        var anime = AnimeDetailsLoader.parseXml(getXmlInput(aniDbAnimeId));
        val animeInfo =  STR."\{anime.getId()} - \{anime.getTitles().stream().findFirst()}";
        log.trace(STR."AnimeXML: \{animeInfo}");
        if (anime.getType() == Anime.Type.TV_Series) {
            log.trace(STR."TV Series: \{animeInfo}. CHecking for tvdb data");
            val tvDbId = this.getAnimeMapping().get((long) aniDbAnimeId).getTvDbId();
            tvDbApi.getTvSeriesData(tvDbId, tvDbAllData -> {
                writeSeriesMetadata(Optional.ofNullable(tvDbAllData), fileInfo, anime, aniDbAnimeId, onDone);
            });
        } else if (anime.getType() == Anime.Type.MOVIE) {

            val tmDbIds = this.getAnimeMapping().get((long) aniDbAnimeId).getTmDbIds();
            if (!tmDbIds.isEmpty()) {
                tmDbApi.getMovieInfo(Integer.parseInt(tmDbIds.get(0)), movieData -> {
                    writeMovieMetadata(Optional.ofNullable(movieData), fileInfo, anime, onDone);
                });
            }
        } else {
            writeSeriesMetadata(Optional.empty(), fileInfo, anime, aniDbAnimeId, onDone);
        }
    }

    private void writeMovieMetadata(Optional<MovieData> movieData, FileInfo fileInfo, Anime anime, OnDone onDone) {
        log.trace(STR."Writing movie metadata for \{anime.getId()} - \{anime.getTitles().stream().findFirst()}");
        val movieBuilder = fileInfo.toMovie();
        anime.updateMovie(movieBuilder);
        movieData.ifPresent(data -> {
            log.info(STR."TMDB: \{data.getId()} - \{data.getTitle()}");
            data.updateMovie(movieBuilder);
        });
        movieBuilder.filePath(fileInfo.getFinalFilePath());
        var movie = movieBuilder.build();
        val generator = MovieNfoWriter.forMovie(movie);
        generator.writeNfoFile(overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_MOVIES));
        exportImages(movie);
        onDone.onDone();
    }

    private void writeSeriesMetadata(Optional<TVSeriesData> tvDbAllData, FileInfo fileInfo, Anime anime, int aniDbAnimeId, OnDone onDone) {
        log.info(STR."Writing series metadata for \{anime.getId()} - \{anime.getTitles().stream().findFirst()}");
        val series = anime.toSeries();
        tvDbAllData.ifPresent(tvDbData -> {
            log.info(STR."using TVDB: \{tvDbData.getSeriesId()} - \{tvDbData.getSeriesName()}");
            tvDbData.updateSeries(series);
        });
        SeriesNfoWriter generator;
        if (nfoGenerators.containsKey((long) aniDbAnimeId)) {
            log.trace(STR."Using existing NFO generator for series \{aniDbAnimeId}");
            generator = nfoGenerators.get((long) aniDbAnimeId);
        } else {
            log.trace(STR."Creating new NFO generator for series \{aniDbAnimeId}");
            generator = SeriesNfoWriter.forSeries(series.build());
            nfoGenerators.put((long) aniDbAnimeId, generator);
        }

        val episodeFileData = fileInfo.toAniDBFileData();
        val filePath = fileInfo.getFinalFilePath();
        val episode = episodeFileData.toEpisode();
        episode.filePath(filePath);
        anime.updateEpisode(episode, episodeFileData.aniDbEpisodeId());
        tvDbAllData.ifPresent(tvDbData -> {
            tvDbData.updateEpisode(episode, episodeFileData.seasonNumber(), episodeFileData.episodeNumber());
        });

        val watchDate = fileInfo.getWatchedDate();
        if (watchDate != null) {
            episode.lastPlayed(watchDate.toLocalDate());
        }

        val episodeData = episode.build();
        generator.writeNfoFiles(episodeData, overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_SERIES),
                overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_EPISODE));
        exportImages(generator.getSeries(), episodeData);
        onDone.onDone();
    }

    private void exportImages(Series series, Episode episode) {
        val seriesFolder = episode.getFilePath().getParent();
        downloadToFile(episode.getThumbnail(), seriesFolder.resolve(STR."\{episode.getFileNameWithoutExtension()}-thumb.jpg"));
        for (var i = 0; i < Math.min(series.getFanarts().size(), MAX_EXPORTED_FANARTS); i++) {
            val url = series.getFanarts().get(i).getUrl();
            val extension = url.substring(url.lastIndexOf("."));
            val name = i == 0 ? "fanart" : STR."fanart\{i}";
            downloadToFile(url, seriesFolder.resolve(STR."\{name}\{extension}"));
        }
        series.getArtworks().stream().filter(a -> a.getType() == Artwork.ArtworkType.SERIES_POSTER).findFirst().ifPresent(
                a -> downloadToFile(a.getUrl(), seriesFolder.resolve("poster.jpg"))
        );
        series.getArtworks().stream().filter(a -> a.getType() == Artwork.ArtworkType.SERIES_BANNER).findFirst().ifPresent(
                a -> downloadToFile(a.getUrl(), seriesFolder.resolve("banner.jpg"))
        );
        val actorPath = seriesFolder.resolve(".actors");
        series.getActors().forEach(actor -> {
            downloadToFile(actor.getThumb(), actorPath.resolve(STR."\{actor.getName().replaceAll(" ", "_")}.jpg"));
        });
    }

    private void exportImages(Movie movie) {
        val movieFolder = movie.getFilePath().getParent();
        downloadToFile(movie.getThumbnail(), movieFolder.resolve(STR."\{movie.getFileNameWithoutExtension()}-thumb.jpg"));
        for (var i = 0; i < Math.min(movie.getFanarts().size(), MAX_EXPORTED_FANARTS); i++) {
            val url = movie.getFanarts().get(i).getUrl();
            val extension = url.substring(url.lastIndexOf("."));
            val name = i == 0 ? "fanart" : STR."fanart\{i}";
            downloadToFile(url, movieFolder.resolve(STR."\{name}\{extension}"));
        }
        val actorPath = movieFolder.resolve(".actors");
        movie.getActors().forEach(actor -> {
            downloadToFile(actor.getThumb(), actorPath.resolve(STR."\{actor.getName().replaceAll(" ", "_")}.jpg"));
        });
    }

    private void downloadToFile(String url, Path path) {
        if (Files.exists(path) && !overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_ARTWORK)) {
            log.debug(STR."File \{path} already exists, not exporting image");
            return;
        }
        downloadHelper.downloadToFile(url, path);
    }

    private InputStream getXmlInput(int aniDbAnimeId) {
        val path = Path.of(STR."\{aniDbAnimeId}.xml");
        try {
            if (!Files.exists(path)) {
                downloadHelper.downloadToFile(AnimeDetailsLoader.getAnidbDetailsXmlUrl(aniDbAnimeId), path);
            }
            return new FileInputStream(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface OnDone {
        void onDone();
    }
}
