package kodi.mapping;

import aniAdd.config.AniConfiguration;
import kodi.mapping.model.Anime;
import kodi.mapping.model.Mapping;
import kodi.mapping.model.SupplementalInfo;
import kodi.mapping.model.Thumb;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Log
@RequiredArgsConstructor
public class AnimeMapping {
    final AniConfiguration aniConfiguration;

    @Getter(lazy = true)
    private final List<Anime> animeMapping = loadAnimeMapping();

    private List<Anime> loadAnimeMapping() {
        try {
//            BufferedInputStream in = new BufferedInputStream(new URI(aniConfiguration.getAnimeMappingUrl()).toURL().openStream());
            val in = new BufferedInputStream(new FileInputStream("anime-list.xml"));
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            val animeList = new LinkedList<Anime>();

            val reader = xmlInputFactory.createXMLEventReader(in);
            var currentAnime = Anime.builder();
            while (reader.hasNext()) {
                val event = reader.nextEvent();
                if (event.isStartElement()) {
                    val startElement = event.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "anime" -> {
                            currentAnime = Anime.builder();
                            currentAnime.aniDbId(getLongAttribute(startElement, "anidbid"));
                            val tvdbId = getAttribute(startElement, "tvdbid").map(Attribute::getValue).orElseThrow();
                            switch (tvdbId.toLowerCase()) {
                                case "movie" -> currentAnime.type(Anime.AnimeType.MOVIE);
                                case "hentai" -> currentAnime.type(Anime.AnimeType.HENTAI);
                                case "ova" -> currentAnime.type(Anime.AnimeType.OVA);
                                case "tv special" -> currentAnime.type(Anime.AnimeType.TVSPECIAL);
                                case "music video" -> currentAnime.type(Anime.AnimeType.MUSIC_VIDEO);
                                case "web" -> currentAnime.type(Anime.AnimeType.WEB);
                                case "other" -> currentAnime.type(Anime.AnimeType.OTHER);
                                default -> currentAnime.tvDbId(Long.parseLong(tvdbId));
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
            return animeList;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XMLStreamException e) {
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

    private int getIntAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Integer.parseInt(a.getValue())).orElseThrow();
    }

    private boolean getBooleanAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Boolean.parseBoolean(a.getValue())).orElse(false);
    }

    private Integer getIntegerAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Integer.parseInt(a.getValue())).orElse(null);
    }

    private Long getLongAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(a -> Long.parseLong(a.getValue())).orElse(null);
    }

    private String getStringAttribute(StartElement startElement, String name) {
        val attribute = getAttribute(startElement, name);
        return attribute.map(Attribute::getValue).orElse(null);
    }


    private Optional<Attribute> getAttribute(StartElement startElement, String name) {
        return Optional.ofNullable(startElement.getAttributeByName(new QName(name)));
    }
}