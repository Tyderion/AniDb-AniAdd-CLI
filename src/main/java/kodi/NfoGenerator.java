package kodi;

import kodi.nfo.Episode;
import kodi.nfo.Series;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import processing.FileInfo;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
public class NfoGenerator {
    private static final String seriesNfo = "tvshow.nfo";
    final Series series;
    final Episode episode;
    final FileInfo fileInfo;

    public void writeNfoFiles() {
        val folder = fileInfo.getRenamedFile() == null ? fileInfo.getFile().getParentFile().toPath() : fileInfo.getRenamedFile().getParent();
        val seriesFile = folder.resolve(seriesNfo);
//        if (!Files.exists(seriesFile)) {
        try {

            val byteArrayOutputStream = new ByteArrayOutputStream();
            val outputStream = new BufferedOutputStream(byteArrayOutputStream);

            val eventFactory = XMLEventFactory.newInstance();
            val outputWriter = XMLOutputFactory.newInstance().createXMLEventWriter(outputStream);
            val rootTag = eventFactory.createStartDocument("UTF-8", "1.0", true);
            outputWriter.add(rootTag);
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("tvshow"), null, null));
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("title"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getTitle()));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("title"), null));
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("originaltitle"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getOriginalTitle()));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("originaltitle"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("showtitle"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getOriginalTitle()));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("showtitle"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("ratings"), null, null));
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("rating"),
                    List.of(eventFactory.createAttribute("default", "true"), eventFactory.createAttribute("max", "10"), eventFactory.createAttribute("name", "anidb")).iterator(),
                    null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("value"), null, null));
            outputWriter.add(eventFactory.createCharacters(String.valueOf(series.getRating())));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("value"), null));
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("votes"), null, null));
            outputWriter.add(eventFactory.createCharacters(String.valueOf(series.getVoteCount())));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("votes"), null));

            outputWriter.add(eventFactory.createEndElement(QName.valueOf("rating"), null));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("ratings"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("plot"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getPlot()));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("plot"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("playcount"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.isWatched() ? "1" : "0"));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("playcount"), null));

            series.getUniqueIds()
                    .forEach(uniqueId -> {
                        try {
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("uniqueid"),
                                    List.of(eventFactory.createAttribute("type", uniqueId.getType().getName())).iterator(),
                                    null));
                            outputWriter.add(eventFactory.createCharacters(STR."\{uniqueId.getValue()}"));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("uniqueid"), null));
                        } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                        }
                    });

            series.getGenres()
                    .forEach(genre -> {
                        try {
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("genre"), null, null));
                            outputWriter.add(eventFactory.createCharacters(genre));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("genre"), null));
                        } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                        }
                    });
            outputWriter.add(eventFactory.createStartElement(QName.valueOf("tag"), null, null));
            outputWriter.add(eventFactory.createCharacters("anime"));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("tag"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("premiered"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getPremiered().format(DateTimeFormatter.ISO_LOCAL_DATE)));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("premiered"), null));

            outputWriter.add(eventFactory.createStartElement(QName.valueOf("studio"), null, null));
            outputWriter.add(eventFactory.createCharacters(series.getStudio()));
            outputWriter.add(eventFactory.createEndElement(QName.valueOf("studio"), null));

            series.getActors()
                    .forEach(actor -> {
                        try {
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("actor"), null, null));
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("name"), null, null));
                            outputWriter.add(eventFactory.createCharacters(actor.getName()));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("name"), null));
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("role"), null, null));
                            outputWriter.add(eventFactory.createCharacters(actor.getRole()));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("role"), null));
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("thumb"), null, null));
                            outputWriter.add(eventFactory.createCharacters(actor.getThumb()));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("thumb"), null));
                            outputWriter.add(eventFactory.createStartElement(QName.valueOf("order"), null, null));
                            outputWriter.add(eventFactory.createCharacters(STR."\{actor.getOrder()}"));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("order"), null));
                            outputWriter.add(eventFactory.createEndElement(QName.valueOf("actor"), null));
                        } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                        }
                    });


            outputWriter.add(eventFactory.createEndElement(QName.valueOf("tvshow"), null));
            outputWriter.add(eventFactory.createEndDocument());
            outputWriter.flush();

            val outputString = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setIndentSize(4);
            format.setSuppressDeclaration(false);
            format.setEncoding(StandardCharsets.UTF_8.displayName());

            org.dom4j.Document document = DocumentHelper.parseText(outputString);
            StringWriter sw = new StringWriter();
            XMLWriter writer = new XMLWriter(sw, format);
            writer.write(document);

            try (val fileWriter = Files.newBufferedWriter(seriesFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                fileWriter.write(sw.toString());
            }


        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } catch (IOException | DocumentException e) {
            throw new RuntimeException(e);
        }
//        }

    }
}
