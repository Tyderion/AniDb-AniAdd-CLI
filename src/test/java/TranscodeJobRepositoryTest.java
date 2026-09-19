import cache.PersistenceConfiguration;
import cache.TranscodeJobRepository;
import cache.entities.TranscodeJob;
import cache.entities.TranscodeJob.Phase;
import config.blocks.TranscodeConfig.Existing;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TranscodeJobRepositoryTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    private Path db;
    private SessionFactory sessionFactory;
    private TranscodeJobRepository repository;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        db = tempDir.resolve("test.sqlite");
        sessionFactory = PersistenceConfiguration.getSessionFactory(db);
        repository = new TranscodeJobRepository(sessionFactory);
    }

    @AfterEach
    public void tearDown() {
        sessionFactory.close();
    }

    @Test
    public void queuesEachReleaseOnceAndFollowsAPendingFile() {
        assertThat(repository.enqueue("hash", 100, Path.of("/input/a.mkv"), Existing.SKIP), is(true));
        assertThat(repository.enqueue("hash", 100, Path.of("/series/Show/a.mkv"), Existing.SKIP), is(true));

        TranscodeJob job = repository.get("hash", 100).orElseThrow();
        assertThat(job.getPhase(), is(Phase.PENDING));
        assertThat(job.getSourcePath(), is("/series/Show/a.mkv"));
    }

    @Test
    public void aFinishedReleaseIsOnlyQueuedAgainWhenRetranscodeIsConfigured() {
        repository.enqueue("hash", 100, Path.of("/a.mkv"), Existing.SKIP);
        TranscodeJob job = repository.claimNext(LEASE).orElseThrow();
        job.setPhase(Phase.DONE);
        job.setAttempts(1);
        job.setLeaseUntil(null);
        repository.save(job);

        assertThat(repository.enqueue("hash", 100, Path.of("/a.mkv"), Existing.SKIP), is(false));
        assertThat(repository.get("hash", 100).orElseThrow().getPhase(), is(Phase.DONE));

        assertThat(repository.enqueue("hash", 100, Path.of("/a.mkv"), Existing.RETRANSCODE), is(true));
        TranscodeJob reset = repository.get("hash", 100).orElseThrow();
        assertThat(reset.getPhase(), is(Phase.PENDING));
        assertThat(reset.getAttempts(), is(0));
    }

    @Test
    public void aClaimedJobIsNotHandedOutAgainUntilItsLeaseExpires() {
        repository.enqueue("hash", 100, Path.of("/a.mkv"), Existing.SKIP);

        assertThat(repository.claimNext(LEASE).isPresent(), is(true));
        assertThat("held by the first claimer", repository.claimNext(LEASE).isPresent(), is(false));

        // A runner that died leaves its lease to expire; the job becomes claimable where its phase says.
        TranscodeJob job = repository.get("hash", 100).orElseThrow();
        job.setPhase(Phase.ENCODED);
        job.setLeaseUntil(System.currentTimeMillis() - 1);
        repository.save(job);
        TranscodeJob resumed = repository.claimNext(LEASE).orElseThrow();
        assertThat(resumed.getPhase(), is(Phase.ENCODED));
    }

    @Test
    public void resumableJobsGoBeforePendingOnes() throws Exception {
        repository.enqueue("pending", 1, Path.of("/pending.mkv"), Existing.SKIP);
        Thread.sleep(5);
        repository.enqueue("swapped", 1, Path.of("/swapped.mkv"), Existing.SKIP);
        TranscodeJob swapped = repository.get("swapped", 1).orElseThrow();
        swapped.setPhase(Phase.SWAPPED);
        repository.save(swapped);

        assertThat(repository.claimNext(LEASE).orElseThrow().getEd2k(), is("swapped"));
        assertThat(repository.claimNext(LEASE).orElseThrow().getEd2k(), is("pending"));
    }

    @Test
    public void twoProcessesRacingForTheSameJobGetItOnce() throws Exception {
        // Separate session factories on one file stand in for the pipeline and transcoder containers.
        repository.enqueue("hash", 100, Path.of("/a.mkv"), Existing.SKIP);
        int claimers = 4;
        List<SessionFactory> factories = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(claimers)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < claimers; i++) {
                SessionFactory factory = PersistenceConfiguration.getSessionFactory(db);
                factories.add(factory);
                TranscodeJobRepository claimer = new TranscodeJobRepository(factory);
                results.add(executor.submit((Callable<Boolean>) () -> {
                    start.await();
                    return claimer.claimNext(LEASE).isPresent();
                }));
            }
            start.countDown();
            int won = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    won++;
                }
            }
            assertThat(won, is(1));
        } finally {
            factories.forEach(SessionFactory::close);
        }
    }

    @Test
    public void theDatabaseRunsInWalMode() {
        String mode = sessionFactory.fromSession(session -> session.createNativeQuery("PRAGMA journal_mode", String.class).getSingleResult());
        assertThat(mode, is("wal"));
    }
}
