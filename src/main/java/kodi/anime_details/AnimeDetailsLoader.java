package kodi.anime_details;

import kodi.anime_details.model.Anime;
import kodi.anime_details.model.AnimeCreator;
import kodi.anime_details.model.Character;
import kodi.anime_details.model.Creator;
import lombok.extern.java.Log;
import lombok.val;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static kodi.XmlHelper.getIntAttribute;
import static kodi.XmlHelper.getStringAttribute;

@Log
public class AnimeDetailsLoader {

    public static Anime parseXml(int animeId) {
        try {
//            BufferedInputStream in = new BufferedInputStream(new URI(aniConfiguration.getAnimeMappingUrl()).toURL().openStream());
            val in = new BufferedInputStream(new FileInputStream("accel_world.xml"));
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            val anime = Anime.builder();

            val reader = xmlInputFactory.createXMLEventReader(in);
            while (reader.hasNext()) {
                val event = reader.nextEvent();
                if (event.isStartElement()) {
                    val startElement = event.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "anime" -> getIntAttribute(startElement, "id");
                        case "type" -> anime.type(reader.getElementText());
                        case "episodecount" -> anime.episodeCount(Integer.parseInt(reader.getElementText()));
                        case "startdate" -> anime.startDate(LocalDate.parse(reader.getElementText()));
                        case "enddate" -> anime.endDate(LocalDate.parse(reader.getElementText()));
                        case "description" -> anime.description(reader.getElementText());
                        case "picture" -> anime.picture(reader.getElementText());
                        case "titles" -> {
                            val titles = parseTitles(reader, startElement);
                            anime.titles(titles);
                        }
                        case "creators" -> {
                            val creators = parseCreators(reader, startElement);
                            anime.creators(creators);
                        }
                        case "characters" -> {
                            val characters = parseCharacters(reader, startElement);
                            anime.characters(characters);
                        }
                        case "ratings" -> {
                            val ratings = parseAnimeRatings(reader, startElement);
                            anime.ratings(ratings);
                        }
                        case "tags" -> {
                            val tags = parseTags(reader, startElement);
                            anime.tags(tags);
                        }
                    }

                }
                if (event.isEndElement()) {
                    if (event.asEndElement().getName().getLocalPart().equals("anime")) {
                        break;
                    }
                }
            }

            return anime.build();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<Anime.Title> parseTitles(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val titles = new HashSet<Anime.Title>();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                if (startElement.getName().getLocalPart().equals("title")) {
                    val title = Anime.Title.builder()
                            .language(getStringAttribute(startElement, "xml:lang"))
                            .type(getStringAttribute(startElement, "type"))
                            .title(reader.getElementText())
                            .build();
                    titles.add(title);
                }
            }
            if (event.isEndElement()) {
                if (event.asEndElement().getName().getLocalPart().equals("titles")) {
                    return titles;
                }

            }
        }
        log.severe("Titles not closed properly");
        return null;
    }

    private static Set<Anime.Rating> parseAnimeRatings(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val ratings = new HashSet<Anime.Rating>();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                val rating = Anime.Rating.builder()
                        .count(getIntAttribute(startElement, "count"))
                        .rating(Double.parseDouble(reader.getElementText()));
                switch (startElement.getName().getLocalPart()) {
                    case "temporary" -> rating.type(Anime.Rating.Type.TEMPORARY);
                    case "permanent" -> rating.type(Anime.Rating.Type.PERMANENT);
                    case "review" -> rating.type(Anime.Rating.Type.REVIEW);
                }
            }
            if (event.isEndElement()) {
                if (event.asEndElement().getName().getLocalPart().equals("ratings")) {
                    return ratings;
                }

            }
        }
        log.severe("Ratings not closed properly");
        return null;
    }


    private static Set<AnimeCreator> parseCreators(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val creators = new HashSet<AnimeCreator>();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                if (startElement.getName().getLocalPart().equals("name")) {
                    val creator = Creator.builder()
                            .id(getIntAttribute(startElement, "id"))
                            .name(reader.getElementText())
                            .build();
                    val animeCreator = AnimeCreator.builder()
                            .creator(creator)
                            .type(getStringAttribute(startElement, "type"))
                            .build();
                    creators.add(animeCreator);
                }
            }
            if (event.isEndElement()) {
                if (event.asEndElement().getName().getLocalPart().equals("creators")) {
                    return creators;
                }

            }
        }
        return null;
    }

    private static Set<Character> parseCharacters(XMLEventReader reader, StartElement rootElement) {

        return null;
    }

    private static void Test() {
        val character = Character.builder()
                .id(1)
                .role(Character.Role.MAIN)
                .rating(Character.Rating.builder()
                        .count(100)
                        .rating(4.5)
                        .build())
                .build();
    }
}
