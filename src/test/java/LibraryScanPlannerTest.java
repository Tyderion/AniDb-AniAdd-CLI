import config.blocks.KodiLibraryScanConfig.Scope;
import config.blocks.KodiLibraryScanConfig.Content;
import kodi.library.LibraryScanPlanner;
import kodi.library.LibraryScanPlanner.ResolvedLibrary;
import kodi.library.LibraryScanPlanner.ScanTarget;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

public class LibraryScanPlannerTest {
    private static final ResolvedLibrary SERIES = new ResolvedLibrary("/storage/media/anime/series/", Path.of("/shows"), Content.TVSHOWS);
    private static final ResolvedLibrary MOVIES = new ResolvedLibrary("smb://nas/anime/movies", Path.of("/movies"), Content.MOVIES);

    private static List<String> directoriesOf(Scope scope, List<ResolvedLibrary> libraries, List<Path> changedFiles) {
        return LibraryScanPlanner.plan(scope, libraries, changedFiles).stream().map(ScanTarget::directory).toList();
    }

    @Test
    public void scansNothingWhenNothingChanged() {
        assertThat(directoriesOf(Scope.LIBRARY, List.of(SERIES, MOVIES), List.of()), empty());
    }

    @Test
    public void libraryScopeScansEveryLibraryWhole() {
        val plan = directoriesOf(Scope.LIBRARY, List.of(SERIES, MOVIES), List.of(Path.of("/shows/Frieren/e01.mkv")));
        assertThat(plan, contains("/storage/media/anime/series/", "smb://nas/anime/movies/"));
    }

    @Test
    public void changedScopeScansTheTopLevelFolderEvenFromASeasonSubfolder() {
        val plan = directoriesOf(Scope.CHANGED, List.of(SERIES, MOVIES), List.of(
                Path.of("/shows/Frieren/e01.mkv"),
                Path.of("/shows/Frieren/Season 2/e02.mkv"),
                Path.of("/movies/Akira/Akira.mkv")));
        assertThat(plan, contains("/storage/media/anime/series/Frieren/", "smb://nas/anime/movies/Akira/"));
    }

    @Test
    public void aFileDirectlyInTheRootScansTheRootAndSwallowsItsSubfolders() {
        val plan = directoriesOf(Scope.CHANGED, List.of(SERIES, MOVIES), List.of(
                Path.of("/movies/Akira/Akira.mkv"),
                Path.of("/movies/Paprika.mkv")));
        assertThat(plan, contains("smb://nas/anime/movies/"));
    }

    @Test
    public void ignoresFilesOutsideEveryLibraryButScansUnmappedLibrariesWhole() {
        val unmapped = new ResolvedLibrary("D:\\anime\\ova", null, Content.TVSHOWS);
        val plan = directoriesOf(Scope.CHANGED, List.of(SERIES, unmapped), List.of(Path.of("/elsewhere/x/e01.mkv")));
        assertThat(plan, contains("D:\\anime\\ova\\"));
    }

    @Test
    public void namesTheLibraryEachScannedDirectoryBelongsTo() {
        val targets = LibraryScanPlanner.plan(Scope.CHANGED, List.of(SERIES, MOVIES), List.of(
                Path.of("/shows/Frieren/e01.mkv"),
                Path.of("/movies/Akira/Akira.mkv")));
        assertThat(targets.stream().map(target -> target.library().content()).toList(), contains(Content.TVSHOWS, Content.MOVIES));
    }

    @Test
    public void picksTheMostSpecificLibraryForNestedLocalPaths() {
        val nested = new ResolvedLibrary("/storage/media/anime/series/kids/", Path.of("/shows/kids"), Content.TVSHOWS);
        val plan = directoriesOf(Scope.CHANGED, List.of(SERIES, nested), List.of(Path.of("/shows/kids/Chi/e01.mkv")));
        assertThat(plan, contains("/storage/media/anime/series/kids/Chi/"));
    }

    @Test
    public void doesNotTreatASiblingWithACommonPrefixAsCovered() {
        val series2 = new ResolvedLibrary("/storage/media/anime/series2/", Path.of("/shows2"), Content.TVSHOWS);
        val plan = directoriesOf(Scope.CHANGED, List.of(SERIES, series2), List.of(
                Path.of("/shows/e01.mkv"),
                Path.of("/shows2/Show/e01.mkv")));
        assertThat(plan, contains("/storage/media/anime/series/", "/storage/media/anime/series2/Show/"));
    }
}
