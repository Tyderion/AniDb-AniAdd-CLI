package kodi;

import kodi.common.UniqueId;
import kodi.nfo.Actor;
import kodi.nfo.Episode;
import kodi.nfo.Series;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import processing.FileInfo;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log
@RequiredArgsConstructor(staticName = "forSeries")
public class NfoGenerator {
    private static final String seriesNfo = "tvshow.nfo";
    final Series series;
    Episode episode;

    private XMLEventWriter writer;
    private XMLEventFactory factory;

    public void writeNfoFiles(Episode episode, Path episodePath, boolean overwriteSeries, boolean overwriteEpisode) {
        this.episode = episode;
        val folder = episodePath.getParent();
        val seriesFile = folder.resolve(seriesNfo);
        val episodeFile = folder.resolve(STR."\{getEpisodeFileName(episodePath)}.nfo");
        log.info(STR."Writing NFO files for \{series.getTitle()}: \{episodeFile.getFileName()}, overwriteSeries: \{overwriteSeries}, overwriteEpisode: \{overwriteEpisode}");
        writeNfoFiles(seriesFile, episodeFile, overwriteSeries, overwriteEpisode);
    }

    private void writeNfoFiles(Path seriesFile, Path episodeFile, boolean overwriteSeries, boolean overwriteEpisode) {
        try {
            if (overwriteSeries || !Files.exists(seriesFile)) {
                log.info(STR."Writing series NFO file: \{seriesFile.toString()}");
                prettyPrint(getSeriesNfoContent(), seriesFile);
            }
            if (overwriteEpisode || !Files.exists(episodeFile)) {
                log.info(STR."Writing episode NFO file: \{episodeFile.toString()}");
                prettyPrint(getEpisodeNfoContent(), episodeFile);
            }
        } catch (XMLStreamException | IOException | DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    private String getEpisodeFileName(Path episodePath) {
        val rootFileName = episodePath.toString();
        return rootFileName.substring(0, rootFileName.lastIndexOf("."));
    }

    private void prettyPrint(String content, Path file) throws IOException, DocumentException {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setIndentSize(4);
        format.setSuppressDeclaration(false);
        format.setEncoding(StandardCharsets.UTF_8.displayName());

        org.dom4j.Document document = DocumentHelper.parseText(content);
        StringWriter sw = new StringWriter();
        XMLWriter writer = new XMLWriter(sw, format);
        writer.write(document);

        try (val fileWriter = Files.newBufferedWriter(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            fileWriter.write(sw.toString());
        }
    }

    private String getEpisodeNfoContent() throws XMLStreamException {
        val byteArrayOutputStream = new ByteArrayOutputStream();
        val outputStream = new BufferedOutputStream(byteArrayOutputStream);

        writer = XMLOutputFactory.newInstance().createXMLEventWriter(outputStream);
        factory = XMLEventFactory.newInstance();

        startDocument();
        writeTag("episodedetails", () -> {
            writeTag("title", episode.getTitle());
            writeTag("showtitle", series.getTitle());
            writeTag("ratings", () -> {
                writeTag("rating", List.of(
                                attribute("default", "true"),
                                attribute("max", "10"),
                                attribute("name", "anidb")),
                        () -> {
                            writeTag("value", episode.getRating());
                            writeTag("votes", episode.getVoteCount());
                        });
            });
            writeTag("season", episode.getSeason());
            writeTag("episode", episode.getEpisode());
            writeTag("plot", episode.getPlot());
            writeTag("runtime", Duration.ofSeconds(episode.getRuntimeInSeconds()).toMinutes());
            writeTag("playcount", episode.isWatched() ? "1" : "0");
            for (UniqueId uniqueId : episode.getUniqueIds()) {
                writeTag("uniqueid", uniqueId.getValue(), attributes("type", uniqueId.getType().getName()));
            }

            for (String genre : episode.getGenres()) {
                writeTag("genre", genre);
            }

            for (String credit : episode.getCredits()) {
                writeTag("credits", credit);
            }

            for (String director : episode.getDirectors()) {
                writeTag("director", director);
            }

            writeTag("premiered", episode.getPremiered().format(DateTimeFormatter.ISO_LOCAL_DATE));
            writeTag("aired", episode.getPremiered().format(DateTimeFormatter.ISO_LOCAL_DATE));
            writeTag("fileinfo", () -> {
                writeTag("streamdetails", () -> {
                    writeTag("video", () -> {
                        val video = episode.getStreamDetails().getVideo();
                        writeTag("codec", video.getCodec());
                        writeTag("aspect", (double) video.getWidth() / video.getHeight());
                        writeTag("width", video.getWidth());
                        writeTag("height", video.getHeight());
                        writeTag("durationinseconds", video.getDurationInSeconds());
                    });
                    writeTag("audio", () -> {
                        val audio = episode.getStreamDetails().getAudio();
                        writeTag("codec", audio.getCodec());
                        writeTag("language", audio.getLanguage());
                        writeTag("channels", audio.getChannels());
                    });
                    for (String subtitle : episode.getStreamDetails().getSubtitles()) {
                        writeTag("subtitle", () -> writeTag("language", subtitle));
                    }
                });
            });
            writeActors();

        });

        writer.flush();

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    private String getSeriesNfoContent() throws XMLStreamException {
        val byteArrayOutputStream = new ByteArrayOutputStream();
        val outputStream = new BufferedOutputStream(byteArrayOutputStream);

        writer = XMLOutputFactory.newInstance().createXMLEventWriter(outputStream);
        factory = XMLEventFactory.newInstance();

        startDocument();
        writeTag("tvshow", () -> {
            writeTag("title", series.getTitle());
            writeTag("originaltitle", series.getOriginalTitle());
            writeTag("showtitle", series.getOriginalTitle());
            writeTag("ratings", () -> {
                writeTag("rating", List.of(
                                attribute("default", "true"),
                                attribute("max", "10"),
                                attribute("name", "anidb")),
                        () -> {
                            writeTag("value", series.getRating());
                            writeTag("votes", series.getVoteCount());
                        });
            });
            writeTag("plot", series.getPlot());
            writeTag("playcount", series.isWatched() ? "1" : "0");

            for (UniqueId uniqueId : series.getUniqueIds()) {
                writeTag("uniqueid", uniqueId.getValue(), attributes("type", uniqueId.getType().getName()));
            }

            for (String genre : series.getGenres()) {
                writeTag("genre", genre);
            }

            writeTag("tag", "anime");
            writeTag("premiered", series.getPremiered().format(DateTimeFormatter.ISO_LOCAL_DATE));
            writeTag("studio", series.getStudio());
            writeActors();

        });
        writer.flush();

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    private void writeActors() throws XMLStreamException {
        for (Actor actor : series.getActors()) {
            writeTag("actor", () -> {
                writeTag("name", actor.getName());
                writeTag("role", actor.getRole());
                writeTag("thumb", actor.getThumb());
                writeTag("order", actor.getOrder());
            });
        }
    }


    private List<Attribute> attributes(String name, String value) {
        return List.of(attribute(name, value));
    }

    private Attribute attribute(String name, String value) {
        return factory.createAttribute(name, value);
    }

    private void startDocument() throws XMLStreamException {
        writer.add(factory.createStartDocument("UTF-8", "1.0", true));
    }

    private void writeTag(String tag, IContentWriter content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        content.write();
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, int content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, long content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, long content, List<Attribute> attributes) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, double content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(String.valueOf(content)));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, String content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), null, null));
        writer.add(factory.createCharacters(content));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, String content, List<Attribute> attributes) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        writer.add(factory.createCharacters(content));
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    private void writeTag(String tag, List<Attribute> attributes, IContentWriter content) throws XMLStreamException {
        writer.add(factory.createStartElement(QName.valueOf(tag), attributes.iterator(), null));
        content.write();
        writer.add(factory.createEndElement(QName.valueOf(tag), null));
    }

    interface IContentWriter {
        void write() throws XMLStreamException;
    }
}
