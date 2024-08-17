package kodi.anime_mapping;

import kodi.anime_mapping.model.AnimeMapping;
import kodi.anime_mapping.model.Mapping;
import kodi.anime_mapping.model.SupplementalInfo;
import kodi.anime_mapping.model.Thumb;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static utils.xml.XmlHelper.*;

@Log
@RequiredArgsConstructor
public class AnimeMappingLoader {
    @NotNull @NonNull final String animeMappingUrl;
    final Duration cacheDuration = Duration.ofDays(31);
    private static final Path LOCAL_ANIME_MAPPING = Path.of("anime-list.xml");

    @Getter(lazy = true)
    private final Map<Long, AnimeMapping> animeMapping = loadAnimeMapping();

    private Map<Long, AnimeMapping> loadAnimeMapping() {
        try {
            val loadFromRepo = !Files.exists(LOCAL_ANIME_MAPPING) ||
                    Files.getLastModifiedTime(LOCAL_ANIME_MAPPING).toInstant().isBefore(Instant.now().minus(cacheDuration));
            try (val in = new BufferedInputStream(loadFromRepo ? new URI(animeMappingUrl).toURL().openStream() : new FileInputStream(LOCAL_ANIME_MAPPING.toFile()))) {
                XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
                val animeList = new LinkedList<AnimeMapping>();

                val reader = xmlInputFactory.createXMLEventReader(in);
                var currentAnime = AnimeMapping.builder();
                while (reader.hasNext()) {
                    val event = reader.nextEvent();
                    if (event.isStartElement()) {
                        val startElement = event.asStartElement();
                        switch (startElement.getName().getLocalPart()) {
                            case "anime" -> {
                                currentAnime = AnimeMapping.builder();
                                currentAnime.aniDbId(getLongAttribute(startElement, "anidbid"));
                                val tmDbIds = getAttribute(startElement, "tmdbid").map(Attribute::getValue).orElse("");
                                Arrays.stream(tmDbIds.split(",")).forEach(currentAnime::tmDbId);
                                val imdbIds = getAttribute(startElement, "tmdbid").map(Attribute::getValue).orElse("");
                                Arrays.stream(imdbIds.split(",")).forEach(currentAnime::imdbId);
                                val tvdbId = getAttribute(startElement, "tvdbid").map(Attribute::getValue).orElseThrow();
                                switch (tvdbId.toLowerCase()) {
                                    case "movie" -> currentAnime.type(AnimeMapping.AnimeType.MOVIE);
                                    case "hentai" -> currentAnime.type(AnimeMapping.AnimeType.HENTAI);
                                    case "ova" -> currentAnime.type(AnimeMapping.AnimeType.OVA);
                                    case "tv special" -> currentAnime.type(AnimeMapping.AnimeType.TVSPECIAL);
                                    case "music video" -> currentAnime.type(AnimeMapping.AnimeType.MUSIC_VIDEO);
                                    case "web" -> currentAnime.type(AnimeMapping.AnimeType.WEB);
                                    case "other" -> currentAnime.type(AnimeMapping.AnimeType.OTHER);
                                    default -> currentAnime.tvDbId(Integer.parseInt(tvdbId));
                                }
                                currentAnime.defaultTvDbSeason(getStringAttribute(startElement, "defaulttvdbseason"));
                            }
                            case "name" -> currentAnime.name(reader.getElementText());
                            case "mapping-list" -> {
                                val mappings = parseMappings(reader);
                                currentAnime.mappings(mappings);
                            }
                            case "supplemental-info" -> {
                                val supplementalInfo = parseSupplementalInfo(reader, startElement);
                                currentAnime.supplementalInfo(supplementalInfo);
                            }
                        }
                    }
                    if (event.isEndElement()) {
                        val endElement = event.asEndElement();
                        if (endElement.getName().getLocalPart().equals("anime")) {
                            val anime = currentAnime.build();
                            animeList.add(anime);
                        }
                    }
                }
                return animeList.stream().collect(Collectors.toMap(AnimeMapping::getAniDbId, a -> a));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private SupplementalInfo parseSupplementalInfo(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val info = SupplementalInfo.builder();

        info.replace(getBooleanAttribute(rootElement, "replace"));
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                switch (startElement.getName().getLocalPart()) {
                    case "studio" -> info.studio(reader.getElementText());
                    case "genre" -> info.genre(reader.getElementText());
                    case "director" -> info.director(reader.getElementText());
                    case "credits" -> info.credit(reader.getElementText());
                    case "fanart" -> {
                        while (reader.hasNext()) {
                            // consume thumb element
                            val thumbEvent = reader.nextEvent();
                            if (thumbEvent.isStartElement()) {
                                val thumbStart = thumbEvent.asStartElement();
                                if (thumbStart.getName().getLocalPart().equals("thumb")) {
                                    val thumb = Thumb.builder()
                                            .url(reader.getElementText())
                                            .dimension(getStringAttribute(thumbStart, "dim"));
                                    info.fanart(thumb.build());
                                    break;
                                }
                            }
                        }
                    }

                }
            }
            if (event.isEndElement()) {
                val endElement = event.asEndElement();
                if (endElement.getName().getLocalPart().equals("supplemental-info")) {
                    return info.build();
                }
            }
        }
        log.severe("Supplemental info not closed properly");
        return null;
    }

    private List<Mapping> parseMappings(XMLEventReader reader) throws XMLStreamException {
        val mappings = new ArrayList<Mapping>();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                switch (startElement.getName().getLocalPart()) {
                    case "mapping" -> {
                        val currentMapping = Mapping.builder();
                        currentMapping.aniDbSeason(getIntAttribute(startElement, "anidbseason"));
                        currentMapping.tvDbSeason(getIntAttribute(startElement, "tvdbseason"));
                        currentMapping.start(getIntegerAttribute(startElement, "start"));
                        currentMapping.end(getIntegerAttribute(startElement, "end"));
                        currentMapping.offset(getIntegerAttribute(startElement, "offset"));
                        val episodeMappings = reader.getElementText().split(";");
                        Arrays.stream(episodeMappings).filter(s -> !s.isBlank()).forEach(s -> {
                            val split = s.split("-");
                            val aniDbEpisode = Integer.parseInt(split[0]);
                            val tvDbEpisodes = Arrays.stream(split[1].split("\\+")).map(Integer::parseInt).toList();
                            currentMapping.mapping(aniDbEpisode, tvDbEpisodes);
                        });
                        mappings.add(currentMapping.build());
                    }
                }
            }
            if (event.isEndElement()) {
                val endElement = event.asEndElement();
                if (endElement.getName().getLocalPart().equals("mapping-list")) {
                    return mappings;
                }
            }
        }
        log.severe("Mapping list not closed properly");
        return List.of();
    }

}
