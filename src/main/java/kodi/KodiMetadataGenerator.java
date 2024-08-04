package kodi;

import aniAdd.config.AniConfiguration;
import cache.AnimeRepository;
import kodi.anime_details.AnimeDetailsLoader;
import kodi.anime_mapping.AnimeMappingLoader;
import kodi.anime_mapping.model.AnimeMapping;
import kodi.tvdb.TvDbApi;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.hibernate.SessionFactory;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class KodiMetadataGenerator {

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<Long, AnimeMapping> animeMapping = initAnimeMapping();
    private final Map<Long, NfoGenerator> nfoGenerators = new HashMap<>();
    private final TvDbApi tvDbApi;
    private final SessionFactory sessionFactory;
    private final AniConfiguration aniConfiguration;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final AnimeRepository animeRepository = initAnimeRepository();

    private Map<Long, AnimeMapping> initAnimeMapping() {
        return new AnimeMappingLoader(aniConfiguration).getAnimeMapping();
    }

    private AnimeRepository initAnimeRepository() {
        return new cache.AnimeRepository(sessionFactory);
    }

    public void generateMetadata(FileInfo fileInfo, boolean overwriteSeries, boolean overwriteEpisode) {
        val aniDbAnimeId = Integer.parseInt(fileInfo.getData().get(TagSystemTags.AnimeId));
        var anime = this.getAnimeRepository().getByAnimeId(aniDbAnimeId).orElseGet(() -> {
            val details = AnimeDetailsLoader.parseXml(getXmlInput(aniDbAnimeId));
            this.getAnimeRepository().saveAnime(details);
            return details;
        });
        val tvDbId = this.getAnimeMapping().get((long) aniDbAnimeId).getTvDbId();
        tvDbApi.getAllTvDbData(tvDbId, data -> {
            data.ifPresent(d -> {
                NfoGenerator generator;
                if (nfoGenerators.containsKey((long) aniDbAnimeId)) {
                    generator = nfoGenerators.get((long) aniDbAnimeId);
                } else {
                    generator = NfoGenerator.forSeries(d.updateSeries(anime.toSeries()).build());
                    nfoGenerators.put((long) aniDbAnimeId, generator);
                }

                generator.writeNfoFiles(d.updateEpisode(fileInfo.toAniDBFileData().toEpisode()).build(), fileInfo.getFinalFilePath(), overwriteSeries, overwriteEpisode);
            });
        });
    }

    private InputStream getXmlInput(int aniDbAnimeId) {
        try {
            if (Files.exists(Path.of(STR."\{aniDbAnimeId}.xml"))) {
                return new FileInputStream(Path.of(STR."\{aniDbAnimeId}.xml").toFile());
            } else {
                return new URI(AnimeDetailsLoader.getAnidbDetailsXmlUrl(aniDbAnimeId)).toURL().openStream();
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void exportActorThumbs() {
        // TODO
    }

    private void generateSeriesNfo() {
        // TODO
    }

    private void generateEpisodeNfo() {
        // TODO
    }
}
