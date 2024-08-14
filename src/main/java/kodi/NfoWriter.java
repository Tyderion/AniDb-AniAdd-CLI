package kodi;

import kodi.nfo.Actor;
import kodi.nfo.Artwork;
import kodi.nfo.Episode;
import kodi.nfo.Rating;
import lombok.val;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;

public abstract class NfoWriter {
    protected XMLEventWriter writer;
    protected XMLEventFactory factory;

    protected static void prettyPrint(String content, Path file) throws IOException, DocumentException {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setIndentSize(4);
        format.setSuppressDeclaration(false);
        format.setEncoding(StandardCharsets.UTF_8.displayName());

        Document document = DocumentHelper.parseText(content);
        StringWriter sw = new StringWriter();
        XMLWriter writer = new XMLWriter(sw, format);
        writer.write(document);

        try (val fileWriter = Files.newBufferedWriter(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            fileWriter.write(sw.toString());
        }
    }

    protected void writePremiered(LocalDate premiered) throws XMLStreamException {
        writeDate("premiered", premiered);
    }

    protected void writeAired(LocalDate premiered) throws XMLStreamException {
        writeDate("aired", premiered);
    }

    protected void writeDate(String tag, LocalDate date) throws XMLStreamException {
        tag(tag, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    protected void writeGenres(List<String> genres) throws XMLStreamException {
        writeList("genre", genres);
    }
    protected void writeTags(List<String> tags) throws XMLStreamException {
        val set = new HashSet<>(tags);
        set.add("anime");
        writeList("tag", set.stream().toList());
    }

    protected void writeStudio(String studio) throws XMLStreamException {
        tag("studio", studio);
    }

    private void writeThumbnail(String thumbnail) throws XMLStreamException {
        tag("thumb", thumbnail);
    }

    private void writeLastPlayed(LocalDate lastPlayed) throws XMLStreamException {
        if (lastPlayed != null) {
            writeDate("lastplayed", lastPlayed);
        }
    }

    private void writeWatched(boolean watched) throws XMLStreamException {
        tag("playcount", watched ? "1" : "0");
    }

    private void writeRuntime(int seconds) throws XMLStreamException {
        tag("runtime", Duration.ofSeconds(seconds).toMinutes());
    }


    protected void writeCredits(List<String> credits) throws XMLStreamException {
        writeList("credits", credits);
    }

    protected void writeDirectors(List<String> credits) throws XMLStreamException {
        writeList("director", credits);
    }

    private void writeList(String tag, List<String> values) throws XMLStreamException {
        for (String value : values) {
            tag(tag, value);
        }
    }

    protected void writeFileDetails(Episode.StreamDetails streamDetails) throws XMLStreamException {
        tag("fileinfo", () -> {
            tag("streamdetails", () -> {
                tag("video", () -> {
                    val video = streamDetails.getVideo();
                    tag("codec", video.getCodec());
                    tag("aspect", (double) video.getWidth() / video.getHeight());
                    tag("width", video.getWidth());
                    tag("height", video.getHeight());
                    tag("durationinseconds", video.getDurationInSeconds());
                });
                tag("audio", () -> {
                    val audio = streamDetails.getAudio();
                    tag("codec", audio.getCodec());
                    tag("language", audio.getLanguage());
                    tag("channels", audio.getChannels());
                });
                for (String subtitle : streamDetails.getSubtitles()) {
                    tag("subtitle", () -> tag("language", subtitle));
                }
            });
        });
    }

    protected void writeFanarts(List<Artwork> fanarts) throws XMLStreamException {
        tag("fanart", () -> {
            for (Artwork artwork : fanarts) {
                tag("thumb", artwork.getUrl());
            }
        });
    }

    protected void writeActors(List<Actor> actors) throws XMLStreamException {
        for (Actor actor : actors) {
            tag("actor", () -> {
                tag("name", actor.getName());
                tag("role", actor.getRole());
                tag("thumb", actor.getThumb());
                tag("order", actor.getOrder());
            });
        }
    }

    protected void writeRatings(List<Rating> ratings) throws XMLStreamException {
        tag("ratings", () -> {
            for (Rating rating : ratings) {
                tag("rating", List.of(
                                attribute("default", "true"),
                                attribute("max", rating.getMax()),
                                attribute("name", rating.getName())),
                        () -> {
                            tag("value", rating.getRating());
                            tag("votes", rating.getVoteCount());
                        });
            }

        });
    }

    protected List<Attribute> attributes(String name, String value) {
        return List.of(attribute(name, value));
    }

    protected Attribute attribute(String name, String value) {
        return factory.createAttribute(name, value);
    }

    protected Attribute attribute(String name, int value) {
        return attribute(name, String.valueOf(value));
    }

    protected void startDocument() throws XMLStreamException {
        writer.add(factory.createStartDocument("UTF-8", "1.0", true));
    }

    protected void tag(String tag, SeriesNfoWriter.IContentWriter content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        content.write();
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, int content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, long content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, long content, List<Attribute> attributes) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, double content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, String content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(content));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, String content, List<Attribute> attributes) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        writer.add(factory.createCharacters(content));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    protected void tag(String tag, List<Attribute> attributes, SeriesNfoWriter.IContentWriter content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        content.write();
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    interface IContentWriter {
        void write() throws XMLStreamException;
    }
}
