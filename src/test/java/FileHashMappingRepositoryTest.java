import cache.FileHashMappingRepository;
import cache.PersistenceConfiguration;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileHashMappingRepositoryTest {

    private SessionFactory sessionFactory;
    private FileHashMappingRepository repository;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        sessionFactory = PersistenceConfiguration.getSessionFactory(tempDir.resolve("test.sqlite"));
        repository = new FileHashMappingRepository(sessionFactory);
    }

    @AfterEach
    public void tearDown() {
        sessionFactory.close();
    }

    @Test
    public void resolvesAConvertedFileToTheFileAniDbKnows() {
        repository.save("newhash", 500, "originalhash", 1000, "original.mkv");

        var mapping = repository.get("newhash", 500);

        assertThat(mapping.isPresent(), is(true));
        assertThat(mapping.get().getOriginalEd2k(), is("originalhash"));
        assertThat(mapping.get().getOriginalSize(), is(1000L));
    }

    @Test
    public void collapsesChainsSoASecondConversionStillPointsAtTheOriginal() {
        repository.save("second", 500, "originalhash", 1000, "original.mkv");
        repository.save("third", 400, "second", 500, "second.mkv");

        var mapping = repository.get("third", 400);

        assertThat(mapping.isPresent(), is(true));
        assertThat(mapping.get().getOriginalEd2k(), is("originalhash"));
        assertThat(mapping.get().getOriginalSize(), is(1000L));
    }

    @Test
    public void knowsNothingAboutFilesItNeverSaw() {
        assertThat(repository.get("unknown", 1).isPresent(), is(false));
        assertThat(repository.get(null, 1).isPresent(), is(false));
    }
}
