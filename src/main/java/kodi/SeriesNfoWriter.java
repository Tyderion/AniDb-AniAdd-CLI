package kodi;

import kodi.nfo.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.val;
import org.dom4j.DocumentException;

import javax.xml.stream.*;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        tag("episodedetails", () -> {
            titles(episode.getTitle(), episode.getOriginalTitle());
            tag("showtitle", series.getTitle());
            ratings(episode.getRatings());
            tag("season", episode.getSeason());
            tag("episode", episode.getEpisode());
            plot(episode.getPlot());
            runtime(episode.getRuntimeInSeconds());
            thumb(episode.getThumbnail());
            watched(episode.isWatched());
            lastPlayed(episode.getLastPlayed());
            uniqueIds(episode.getUniqueIds());
            studio(series.getStudio());
            genres(episode.getGenres());
            credits(episode.getCredits());
            directors(episode.getDirectors());
            premiered(episode.getPremiered());
            aired(episode.getPremiered());
            fileDetails(episode.getStreamDetails());
            actors(series.getActors());

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
        tag("tvshow", () -> {
            titles(series.getTitle(), series.getOriginalTitle());
            tag("showtitle", series.getOriginalTitle());
            ratings(series.getRatings());
            plot(series.getPlot());
            tag("status", series.getStatus());

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
                thumb(artwork.getUrl(), attributes);
            }
            fanarts(series.getFanarts());
            uniqueIds(series.getUniqueIds());

            genres(series.getGenres());
            tags(List.of(series.getTag()));
            premiered(series.getPremiered());
            studio(series.getStudio());
            actors(series.getActors());

        });
        writer.flush();

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }
}
