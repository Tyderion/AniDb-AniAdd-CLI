package kodi;

import kodi.common.UniqueId;
import kodi.nfo.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Log
@RequiredArgsConstructor(staticName = "forSeries")
public class SeriesNfoWriter extends NfoWriter {
    private static final String seriesNfo = "tvshow.nfo";
    @Getter final Series series;
    Episode episode;

    public void writeNfoFiles(Episode episode, boolean overwriteSeries, boolean overwriteEpisode) {
        this.episode = episode;
        val folder = episode.getFilePath().getParent();
        val seriesFile = folder.resolve(seriesNfo);
        val episodeFile = folder.resolve(STR."\{episode.getFileNameWithoutExtension()}.nfo");
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

    private String getEpisodeNfoContent() throws XMLStreamException {
        val byteArrayOutputStream = new ByteArrayOutputStream();
        val outputStream = new BufferedOutputStream(byteArrayOutputStream);

        writer = XMLOutputFactory.newInstance().createXMLEventWriter(outputStream);
        factory = XMLEventFactory.newInstance();

        startDocument();
        writeTag("episodedetails", () -> {
            writeTag("title", episode.getTitle());
            writeTag("showtitle", series.getTitle());
            writeRatings(episode.getRatings());
            writeTag("season", episode.getSeason());
            writeTag("episode", episode.getEpisode());
            writeTag("plot", episode.getPlot());
            writeRuntime(episode.getRuntimeInSeconds());
            writeThumbnail(episode.getThumbnail());
            writeWatched(episode.isWatched());
            writeLastPlayed(episode.getLastPlayed());
            writeUniqueIds(episode.getUniqueIds());
            writeStudio(series.getStudio());
            writeGenres(episode.getGenres());
            writeCredits(episode.getCredits());
            writeDirectors(episode.getDirectors());
            writePremiered(episode.getPremiered());
            writeAired(episode.getPremiered());
            writeFileDetails(episode.getStreamDetails());
            writeActors(series.getActors());

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
            writeRatings(series.getRatings());
            writeTag("plot", series.getPlot());
            writeTag("status", series.getStatus());

            for (Artwork artwork : series.getArtworks()) {
                val attributes = switch (artwork.getType()) {
                    case SERIES_POSTER, SERIES_BACKGROUND -> List.of(attribute("aspect", "poster"));
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
                writeTag("thumb", artwork.getUrl(), attributes);
            }
            writeFanarts(series.getFanarts());
            writeUniqueIds(series.getUniqueIds());

            writeGenres(series.getGenres());
            writeTags(List.of(series.getTag()));
            writePremiered(series.getPremiered());
            writeStudio(series.getStudio());
            writeActors(series.getActors());

        });
        writer.flush();

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    private void writeUniqueIds(List<UniqueId> series) throws XMLStreamException {
        for (UniqueId uniqueId : series) {
            writeTag("uniqueid", uniqueId.getValue(), attributes("type", uniqueId.getType().getName()));
        }
    }
}
