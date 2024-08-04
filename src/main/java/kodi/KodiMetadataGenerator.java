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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Log
@RequiredArgsConstructor
public class KodiMetadataGenerator {

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<Long, AnimeMapping> animeMapping = initAnimeMapping();
    private final Map<Long, NfoGenerator> nfoGenerators = new HashMap<>();
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

    public void generateMetadata(FileInfo fileInfo, boolean overwriteSeries, boolean overwriteEpisode) {
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
                val episodeData = tvDbData.updateEpisode(episodeFileData.toEpisode(), episodeFileData.seasonNumber(), episodeFileData.episodeNumber())
                        .filePath(filePath)
                        .fileExtension(extension)
                        .fileNameWithoutExtension(fileNameWithoutExtension)
                                .build();


                generator.writeNfoFiles(episodeData, overwriteSeries, overwriteEpisode);
                exportImages(generator.getSeries(), episodeData);
            });
        });
    }

    private void exportImages(Series series, Episode episode) {
        val bytes = downloadFile(episode.getThumbnail());
        val path = episode.getFilePath().getParent().resolve(STR."\{episode.getFileNameWithoutExtension()}-thumb.jpg");
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] downloadFile(String url) {
        try (val is = new URI(url).toURL().openStream()) {
            return is.readAllBytes();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream getXmlInput(int aniDbAnimeId) {
        val path = Path.of(STR."\{aniDbAnimeId}.xml");
        try {
            if (Files.exists(path)) {
                return new FileInputStream(path.toFile());
            } else {
                try (val is = new URI(AnimeDetailsLoader.getAnidbDetailsXmlUrl(aniDbAnimeId)).toURL().openStream()) {
                    Files.copy(is, Path.of(STR."\{aniDbAnimeId}_default.xml"));
                }
                return getXmlInput(aniDbAnimeId);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
