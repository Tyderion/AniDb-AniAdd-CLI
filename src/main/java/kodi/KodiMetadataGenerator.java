package kodi;

import cache.AnimeRepository;
import kodi.anime_details.AnimeDetailsLoader;
import kodi.anime_mapping.AnimeMappingLoader;
import kodi.anime_mapping.model.AnimeMapping;
import kodi.nfo.Episode;
import kodi.nfo.Series;
import kodi.tvdb.TvDbApi;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.hibernate.SessionFactory;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Log
@RequiredArgsConstructor
public class KodiMetadataGenerator {

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<Long, AnimeMapping> animeMapping = initAnimeMapping();
    private final Map<Long, NfoGenerator> nfoGenerators = new HashMap<>();
    private final DownloadHelper downloadHelper;
    private final TvDbApi tvDbApi;
    private final SessionFactory sessionFactory;
    private final String animeMappingUrl;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final AnimeRepository animeRepository = initAnimeRepository();

    private Map<Long, AnimeMapping> initAnimeMapping() {
        return new AnimeMappingLoader(animeMappingUrl).getAnimeMapping();
    }

    private AnimeRepository initAnimeRepository() {
        return new cache.AnimeRepository(sessionFactory);
    }

    public void generateMetadata(FileInfo fileInfo, boolean overwriteSeries, boolean overwriteEpisode, OnDone onDone) {
        log.info(STR."Generating metadata for \{fileInfo.getFile().getName()}");
        val aniDbAnimeId = Integer.parseInt(fileInfo.getData().get(TagSystemTags.AnimeId));
        var anime = this.getAnimeRepository().getByAnimeId(aniDbAnimeId).orElseGet(() -> {
            val details = AnimeDetailsLoader.parseXml(getXmlInput(aniDbAnimeId));
            this.getAnimeRepository().saveAnime(details);
            return details;
        });
        log.info(STR."Anime: \{anime.getId()} - \{anime.getTitles().stream().findFirst()}");
        val tvDbId = this.getAnimeMapping().get((long) aniDbAnimeId).getTvDbId();
        tvDbApi.getAllTvDbData(tvDbId, data -> {
            data.ifPresent(tvDbData -> {
                log.info(STR."TVDB: \{tvDbData.getSeriesId()} - \{tvDbData.getSeriesName()}");
                NfoGenerator generator;
                if (nfoGenerators.containsKey((long) aniDbAnimeId)) {
                    generator = nfoGenerators.get((long) aniDbAnimeId);
                } else {
                    generator = NfoGenerator.forSeries(tvDbData.updateSeries(anime.toSeries()).build());
                    nfoGenerators.put((long) aniDbAnimeId, generator);
                }

                val episodeFileData = fileInfo.toAniDBFileData();
                val filePath = fileInfo.getFinalFilePath();
                val fileName = filePath.getFileName().toString();
                val extension = fileName.substring(fileName.lastIndexOf("."));
                val fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
                val episode = episodeFileData.toEpisode();
                anime.updateEpisode(episode, episodeFileData.aniDbEpisodeNumber());
                tvDbData.updateEpisode(episode, episodeFileData.seasonNumber(), episodeFileData.episodeNumber());

                val watchDate = fileInfo.getWatchedDate();
                if (watchDate != null) {
                    episode.lastPlayed(fileInfo.getWatchedDate().toLocalDate());
                }

                val episodeData = episode
                        .filePath(filePath)
                        .fileExtension(extension)
                        .fileNameWithoutExtension(fileNameWithoutExtension)
                        .build();


                generator.writeNfoFiles(episodeData, overwriteSeries, overwriteEpisode);
                exportImages(generator.getSeries(), episodeData);
                onDone.onDone();
            });
        });
    }

    private void exportImages(Series series, Episode episode) {
        val seriesFolder = episode.getFilePath().getParent();
        writeFile(episode.getThumbnail(), seriesFolder.resolve(STR."\{episode.getFileNameWithoutExtension()}-thumb.jpg"));

        for (var i = 0; i < series.getFanarts().size(); i++) {
            val url = series.getFanarts().get(i).getUrl();
            val extension = url.substring(url.lastIndexOf("."));
            val name = i == 0 ? "fanart" : STR."fanart\{i}";
            writeFile(url, seriesFolder.resolve(STR."\{name}\{extension}"));
        }
        series.getArtworks().stream().filter(a -> a.getType() == Series.ArtworkType.SERIES_POSTER).findFirst().ifPresent(
                a -> writeFile(a.getUrl(), seriesFolder.resolve("poster.jpg"))
        );
        series.getArtworks().stream().filter(a -> a.getType() == Series.ArtworkType.SERIES_BANNER).findFirst().ifPresent(
                a -> writeFile(a.getUrl(), seriesFolder.resolve("banner.jpg"))
        );
        val actorPath = seriesFolder.resolve(".actors");
        series.getActors().forEach(actor -> {
            writeFile(actor.getThumb(), actorPath.resolve(STR."\{actor.getName().replaceAll(" ", "_")}.jpg"));
        });
    }

    private void writeFile(String url, Path path) {
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            downloadHelper.downloadToFile(url, path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
