package kodi;

import kodi.common.UniqueId;
import kodi.nfo.*;
import lombok.val;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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

    protected String newFile(String rootTag, @NotNull IContentWriter content) throws XMLStreamException {
        val byteArrayOutputStream = new ByteArrayOutputStream();
        val outputStream = new BufferedOutputStream(byteArrayOutputStream);

        writer = XMLOutputFactory.newInstance().createXMLEventWriter(outputStream);
        factory = XMLEventFactory.newInstance();

        writer.add(factory.createStartDocument("UTF-8", "1.0", true));
        tag(rootTag, content);
        writer.flush();
        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    protected void premiered(LocalDate premiered) throws XMLStreamException {
        date("premiered", premiered);
    }

    protected void aired(LocalDate premiered) throws XMLStreamException {
        date("aired", premiered);
    }

    protected void date(String tag, LocalDate date) throws XMLStreamException {
        tag(tag, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    protected void genres(List<String> genres) throws XMLStreamException {
        list("genre", genres);
    }

    protected void titles(String title, String originalTitle) throws XMLStreamException {
        tag("title", title);
        tag("originaltitle", originalTitle);
    }

    protected void tags(List<String> tags) throws XMLStreamException {
        val set = new HashSet<>(tags);
        set.add("anime");
        list("tag", set.stream().toList());
    }

    protected void studio(String studio) throws XMLStreamException {
        tag("studio", studio);
    }

    protected void lastPlayed(LocalDate lastPlayed) throws XMLStreamException {
        if (lastPlayed != null) {
            date("lastplayed", lastPlayed);
        }
    }

    protected void watched(boolean watched) throws XMLStreamException {
        tag("playcount", watched ? "1" : "0");
    }

    protected void runtime(int seconds) throws XMLStreamException {
        tag("runtime", Duration.ofSeconds(seconds).toMinutes());
    }


    protected void credits(List<String> credits) throws XMLStreamException {
        list("credits", credits);
    }

    protected void directors(List<String> credits) throws XMLStreamException {
        list("director", credits);
    }

    protected void list(String tag, List<String> values) throws XMLStreamException {
        for (String value : values) {
            tag(tag, value);
        }
    }

    protected void uniqueIds(List<UniqueId> series) throws XMLStreamException {
        for (UniqueId uniqueId : series) {
            tag("uniqueid", uniqueId.getValue(), attributes("type", uniqueId.getType().getName()));
        }
    }

    protected void fileDetails(StreamDetails streamDetails) throws XMLStreamException {
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

    protected void fanarts(List<Artwork> fanarts) throws XMLStreamException {
        tag("fanart", () -> {
            artworks(fanarts);
        });
    }

    protected void actors(List<Actor> actors) throws XMLStreamException {
        for (Actor actor : actors) {
            tag("actor", () -> {
                tag("name", actor.getName());
                tag("role", actor.getRole());
                thumb(actor.getThumb());
                tag("order", actor.getOrder());
            });
        }
    }

    protected void artworks(List<Artwork> artworks) throws XMLStreamException {
        for (Artwork artwork : artworks) {
            val attributes = switch (artwork.getType()) {
                case SERIES_BACKGROUND, MOVIE_BACKGROUND -> List.of(attribute("aspect", "landscape"));
                case SERIES_POSTER, MOVIE_POSTER -> List.of(attribute("aspect", "poster"));
                case SERIES_BANNER -> List.of(attribute("aspect", "banner"));
                case SEASON_BANNER -> List.of(
                        attribute("aspect", "banner"),
                        attribute("type", "season"),
                        attribute("originalseason", artwork.getSeason()),
                        attribute("season", 1));
                case SEASON_POSTER, SEASON_BACKGROUND -> List.of(attribute("aspect", "poster"),
                        attribute("type", "season"),
                        attribute("originalseason", artwork.getSeason()),
                        attribute("season", 1));
                case CLEARART -> List.of(attribute("aspect", "clearart"));
                case CLEARLOGO -> List.of(attribute("aspect", "clearlogo"));
            };
            thumb(artwork.getUrl(), attributes);
        }
    }

    protected void ratings(List<Rating> ratings) throws XMLStreamException {
        tag("ratings", () -> {
            var defaultTrue = true;
            for (Rating rating : ratings) {
                tag("rating", List.of(
                                attribute("default", defaultTrue ? "true" : "false"),
                                attribute("max", rating.getMax()),
                                attribute("name", rating.getName())),
                        () -> {
                            tag("value", rating.getRating());
                            tag("votes", rating.getVoteCount());
                        });
                defaultTrue = false;
            }

        });
    }

    protected void plot(@NotNull String plot) throws XMLStreamException {
        tag("plot", plot);
    }

    protected void thumb(String url) throws XMLStreamException {
        tag("thumb", url, List.of());
    }

    protected void thumb(String url, List<Attribute> attributes) throws XMLStreamException {
        tag("thumb", url, attributes);
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

    protected interface IContentWriter {
        void write() throws XMLStreamException;
    }
}
