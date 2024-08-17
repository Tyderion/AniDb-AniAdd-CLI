package kodi.nfo;

import kodi.nfo.model.Movie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dom4j.DocumentException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor(staticName = "forMovie")
public class MovieNfoWriter extends NfoWriter {
    final Movie movie;

    public void writeNfoFile(boolean overwrite) {
        val moviesFile = movie.getFilePath().getParent().resolve(STR."\{movie.getFileNameWithoutExtension()}.nfo");
        log.info(STR."Writing NFO files for \{movie.getTitle()}: \{movie.getFileName()}, overwrite: \{overwrite}");
        writeMovieNfo(moviesFile, overwrite);
    }

    private void writeMovieNfo(Path movieFile, boolean overwrite) {
        try {
            if (overwrite || !Files.exists(movieFile)) {
                log.info(STR."Writing movie NFO file: \{movieFile.toString()}");
                writeToFile(getMovieNfoContent(), movieFile);
            }
        } catch (XMLStreamException | IOException | DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    private String getMovieNfoContent() throws XMLStreamException {
        return newFile("movie", () -> {
            titles(movie.getTitle(), movie.getOriginalTitle());
            ratings(movie.getRatings());
            plot(movie.getPlot());
            tag("outline", movie.getOutline());
            runtime(movie.getRuntimeInSeconds());
            thumb(movie.getThumbnail());
            watched(movie.isWatched());
            lastPlayed(movie.getLastPlayed());
            uniqueIds(movie.getUniqueIds());
            genres(movie.getGenres());
            fanarts(movie.getFanarts());
            credits(movie.getCredits());
            directors(movie.getDirectors());
            premiered(movie.getPremiered());
            aired(movie.getPremiered());
            tag("trailer", movie.getTrailer());
            fileDetails(movie.getStreamDetails());
            actors(movie.getActors());
        });
    }
}
