package kodi.anime_details;

import kodi.anime_details.model.*;
import kodi.anime_details.model.Character;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static kodi.XmlHelper.getIntAttribute;
import static kodi.XmlHelper.getStringAttribute;

@Log
public class AnimeDetailsLoader {

    public static Anime parseXml(String xml) {
        try {
//            BufferedInputStream in = new BufferedInputStream(new URI(aniConfiguration.getAnimeMappingUrl()).toURL().openStream());
            val in = new BufferedInputStream(new FileInputStream(xml));
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            val anime = Anime.builder();

            val reader = xmlInputFactory.createXMLEventReader(in);
            var animeId = 0;
            while (reader.hasNext()) {
                val event = reader.nextEvent();
                if (event.isStartElement()) {
                    val startElement = event.asStartElement();
                    switch (startElement.getName().getLocalPart()) {
                        case "anime" -> {
                            animeId = getIntAttribute(startElement, "id");
                            anime.id(animeId);
                        }
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
                            val creators = parseCreators(reader, startElement, animeId);
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
                            val tags = parseTags(reader, startElement, animeId);
                            anime.tags(tags);
                        }
                        case "episodes" -> {
                            val episodes = parseEpisodes(reader, startElement);
                            anime.episodes(episodes);
                        }
                        case "relatedanime", "similaranime", "recommendations", "resources" ->
                                consumeUntilEndTag(reader, startElement.getName().getLocalPart());
                    }

                }
                if (event.isEndElement()) {
                    if (event.asEndElement().getName().getLocalPart().equals("anime")) {
                        return anime.build();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static void consumeUntilEndTag(XMLEventReader reader, String endTag) throws XMLStreamException {
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals(endTag)) {
                return;
            }
        }
    }

    private static Set<Character> parseCharacters(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val characters = new HashSet<Character>();
        var currentCharacter = Character.builder();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                switch (startElement.getName().getLocalPart()) {
                    case "character" -> {
                        currentCharacter = Character.builder()
                                .id(getIntAttribute(startElement, "id"));
                        switch (getStringAttribute(startElement, "type")) {
                            case "main character in" -> currentCharacter.role(Character.Role.MAIN);
                            case "secondary cast in" -> currentCharacter.role(Character.Role.SECONDARY);
                            case "appears in" -> currentCharacter.role(Character.Role.APPEARS_IN);
                        }
                    }
                    case "rating" -> {
                        currentCharacter.rating(Character.Rating.builder()
                                .count(getIntAttribute(startElement, "votes"))
                                .rating(Double.parseDouble(reader.getElementText()))
                                .build());
                    }
                    case "name" -> currentCharacter.name(reader.getElementText());
                    case "gender" -> currentCharacter.gender(reader.getElementText());
                    case "picture" -> currentCharacter.picture(reader.getElementText());
                    case "seiyuu" -> {
                        val seiyuu = Creator.builder()
                                .id(getIntAttribute(startElement, "id"))
                                .picture(getStringAttribute(startElement, "picture"))
                                .name(reader.getElementText())
                                .build();
                        currentCharacter.seiyuu(seiyuu);
                    }
                }
            }
            if (event.isEndElement()) {
                switch (event.asEndElement().getName().getLocalPart()) {
                    case "character" -> {
                        val character = currentCharacter.build();
                        if (character.getRole() != Character.Role.APPEARS_IN) {
                            characters.add(character);
                        }
                    }
                    case "characters" -> {
                        return characters;
                    }
                }

            }
        }
        log.severe("characters not closed properly");
        return null;
    }

    private static Set<Episode> parseEpisodes(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val episodes = new HashSet<Episode>();
        var currentEpisode = Episode.builder();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                switch (startElement.getName().getLocalPart()) {
                    case "episode" -> {
                        currentEpisode = Episode.builder()
                                .id(getIntAttribute(startElement, "id"));
                    }
                    case "airdate" -> {
                        currentEpisode.airDate(LocalDate.parse(reader.getElementText()));
                    }
                    case "rating" -> {
                        currentEpisode.voteCount(getIntAttribute(startElement, "votes"));
                        currentEpisode.rating(Double.parseDouble(reader.getElementText()));
                    }
                }
            }
            if (event.isEndElement()) {
                switch (event.asEndElement().getName().getLocalPart()) {
                    case "episode" -> {
                        episodes.add(currentEpisode.build());
                    }
                    case "episodes" -> {
                        return episodes;
                    }
                }

            }
        }
        log.severe("Episodes not closed properly");
        return null;
    }

    private static Set<AnimeTag> parseTags(XMLEventReader reader, StartElement rootElement, int animeId) throws XMLStreamException {
        val tags = new HashSet<AnimeTag>();
        var currentTag = Tag.builder();
        var currentAnimeTag = AnimeTag.builder();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                switch (startElement.getName().getLocalPart()) {
                    case "tag" -> {
                        currentTag = Tag.builder().id(getIntAttribute(startElement, "id"));
                        currentAnimeTag = AnimeTag.builder().weight(getIntAttribute(startElement, "weight"));
                    }
                    case "name" -> currentTag.name(reader.getElementText());
                    case "description" -> currentTag.description(reader.getElementText());
                }
            }
            if (event.isEndElement()) {
                switch (event.asEndElement().getName().getLocalPart()) {
                    case "tag" -> {
                        val tag = currentTag.build();
                        currentAnimeTag.tag(tag);
                        currentAnimeTag.animeId(animeId).tagId(tag.getId());
                        val animeTag = currentAnimeTag.build();
                        if (animeTag.getWeight() > 0) {
                            tags.add(animeTag);
                        }
                    }
                    case "tags" -> {
                        return tags;
                    }
                }

            }
        }
        log.severe("Tags not closed properly");
        return null;
    }

    private static Set<String> relevantLanguages = new HashSet<>(Arrays.asList("ja", "en", "x-jat"));

    private static Set<Anime.Title> parseTitles(XMLEventReader reader, StartElement rootElement) throws XMLStreamException {
        val titles = new HashSet<Anime.Title>();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                if (startElement.getName().getLocalPart().equals("title")) {
                    val title = Anime.Title.builder()
                            .language(getStringAttribute(startElement, "lang", "http://www.w3.org/XML/1998/namespace"))
                            .type(getStringAttribute(startElement, "type"))
                            .title(reader.getElementText())
                            .build();
                    if (relevantLanguages.contains(title.getLanguage()) && (title.getType().equals("main") || title.getType().equals("official"))) {
                        titles.add(title);
                    }
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
        var currentRating = Anime.Rating.builder();
        while (reader.hasNext()) {
            val event = reader.nextEvent();
            if (event.isStartElement()) {
                val startElement = event.asStartElement();
                currentRating = Anime.Rating.builder()
                        .count(getIntAttribute(startElement, "count"))
                        .rating(Double.parseDouble(reader.getElementText()));
                switch (startElement.getName().getLocalPart()) {
                    case "temporary" -> currentRating.type(Anime.Rating.Type.TEMPORARY);
                    case "permanent" -> currentRating.type(Anime.Rating.Type.PERMANENT);
                    case "review" -> currentRating.type(Anime.Rating.Type.REVIEW);
                }
                ratings.add(currentRating.build());
            }
            if (event.isEndElement()) {
                switch (event.asEndElement().getName().getLocalPart()) {
                    case "ratings" -> {
                        return ratings;
                    }
                }

            }
        }
        log.severe("Ratings not closed properly");
        return null;
    }


    private static Set<AnimeCreator> parseCreators(XMLEventReader reader, StartElement rootElement, int animeId) throws XMLStreamException {
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
                    val type = getStringAttribute(startElement, "type");
                    val animeCreator = AnimeCreator.builder()
                            .creator(creator);
                    switch (type) {
                        case "Direction" -> animeCreator.type(AnimeCreator.Type.DIRECTION);
                        case "Original Work" -> animeCreator.type(AnimeCreator.Type.ORIGINAL_WORK);
                        case "Character Design" -> animeCreator.type(AnimeCreator.Type.CHARACTER_DESIGNER);
                        case "Animation Work" -> animeCreator.type(AnimeCreator.Type.ANIMATION_WORK);
                    }
                    val ac = animeCreator.build();
                    if (ac.getType() != null) {
                        creators.add(ac);
                    }
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
}
