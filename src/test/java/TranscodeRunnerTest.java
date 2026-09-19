import cache.AniDBFileRepository;
import cache.FileHashMappingRepository;
import cache.PersistenceConfiguration;
import cache.TranscodeJobRepository;
import cache.entities.AniDBFileData;
import cache.entities.TranscodeJob;
import cache.entities.TranscodeJob.Phase;
import config.blocks.FileConfig;
import config.blocks.MoveConfig;
import config.blocks.RenameConfig;
import config.blocks.TagsConfig;
import config.blocks.TranscodeConfig;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import processing.FileHandler;
import processing.FileParser;
import processing.FileRenamer;
import processing.tagsystem.TagSystemTags;
import transcode.MediaProber;
import transcode.TranscodeRunner;
import transcode.Transcoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The transcoder process end to end against a temp sqlite file and real ffmpeg: a queued job becomes a
 * renamed, mapped, converted file with its NFO and thumbnail following it, and a job interrupted after
 * encoding resumes without encoding again.
 */
public class TranscodeRunnerTest {
    private static final String ORIGINAL_ED2K = "0123456789abcdef0123456789abcdef";
    private static final String TAG_SYSTEM = "FileName:=%ATr% \" - \" %EpNo% \" [\" %FVCodec% \"][\" $uc(%FCrc%) \"]\"";
    private static final String NFO = """
            <episodedetails><title>One</title><fileinfo><streamdetails><video><codec>H264/AVC</codec><width>320</width></video>\
            <audio><codec>aac</codec></audio></streamdetails></fileinfo></episodedetails>""";

    private static boolean ffmpegAvailable;

    private Path show;
    private Path originals;
    private SessionFactory sessionFactory;
    private TranscodeJobRepository jobs;
    private AniDBFileRepository files;
    private FileHashMappingRepository mappings;

    @BeforeAll
    public static void checkFfmpeg() {
        ffmpegAvailable = TranscoderTest.canRun("ffmpeg") && TranscoderTest.canRun("ffprobe");
    }

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws Exception {
        show = Files.createDirectories(tempDir.resolve("series/Show"));
        originals = tempDir.resolve("originals");
        sessionFactory = PersistenceConfiguration.getSessionFactory(tempDir.resolve("cache.sqlite"));
        jobs = new TranscodeJobRepository(sessionFactory);
        files = new AniDBFileRepository(sessionFactory);
        mappings = new FileHashMappingRepository(sessionFactory);
    }

    @AfterEach
    public void tearDown() {
        sessionFactory.close();
    }

    private TranscodeConfig.TranscodeConfigBuilder config() {
        return TranscodeConfig.builder()
                .mode(TranscodeConfig.Mode.QUEUE)
                .videoArgs(TranscoderTest.FAST_VIDEO_ARGS)
                .nice(0)
                .original(MoveConfig.HandlingConfig.builder().mode(MoveConfig.HandlingConfig.Mode.MOVE).folder(originals).build());
    }

    private TranscodeRunner runner(TranscodeConfig config) {
        FileHandler fileHandler = new FileHandler();
        FileConfig fileConfig = FileConfig.builder()
                .rename(RenameConfig.builder().mode(RenameConfig.Mode.TAGSYSTEM).related(true).build())
                .build();
        TagsConfig tags = TagsConfig.builder().tagSystem(TAG_SYSTEM).build();
        MediaProber prober = new MediaProber("ffprobe");
        return new TranscodeRunner(config, fileConfig, jobs, mappings, files, new Transcoder(config, prober, fileHandler),
                prober, new FileRenamer(fileHandler, tags), fileHandler);
    }

    /** A library episode as the pipeline leaves it: named, with NFO and thumbnail, cached and queued. */
    private Path queuedEpisode() throws Exception {
        Path source = TranscoderTest.generateSource(show.resolve("Show - 01 [H264AVC][AAAAAAAA].mkv"));
        Files.writeString(show.resolve("Show - 01 [H264AVC][AAAAAAAA].nfo"), NFO);
        Files.writeString(show.resolve("Show - 01 [H264AVC][AAAAAAAA]-thumb.jpg"), "thumb");
        Files.writeString(show.resolve("Show - 010 [H264AVC][BBBBBBBB].nfo"), "a different episode");
        files.saveAniDBFileData(AniDBFileData.builder()
                .ed2k(ORIGINAL_ED2K)
                .size(Files.size(source))
                .fileName(source.getFileName().toString())
                .folderName("Show")
                .tags(Map.of(TagSystemTags.SeriesNameRomaji, "Show",
                        TagSystemTags.EpisodeNumber, "01",
                        TagSystemTags.FileVideoCodec, "H264/AVC",
                        TagSystemTags.FileCrc, "aaaaaaaa"))
                .build());
        jobs.enqueue(ORIGINAL_ED2K, Files.size(source), source, TranscodeConfig.Existing.SKIP);
        return source;
    }

    private static String ed2k(Path file) {
        AtomicReference<String> hash = new AtomicReference<>();
        new FileParser(file.toFile(), 0, (_, ed2k, _) -> hash.set(ed2k), () -> false).run();
        return hash.get();
    }

    @Test
    public void aQueuedEpisodeEndsUpConvertedRenamedAndMapped() throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = queuedEpisode();
        long originalSize = Files.size(source);

        assertThat(runner(config().build()).runOnce(), is(true));

        TranscodeJob job = jobs.get(ORIGINAL_ED2K, originalSize).orElseThrow();
        assertThat(job.getLastError(), job.getPhase(), is(Phase.DONE));
        Path converted = Path.of(job.getConvertedPath());
        assertThat(converted.getFileName().toString(), is(STR."Show - 01 [HEVC][\{job.getLocalCrc32().toUpperCase()}].mkv"));
        assertThat(Files.exists(converted), is(true));
        assertThat("the original is moved aside", Files.exists(originals.resolve(source.getFileName())), is(true));
        assertThat(Files.exists(source), is(false));

        String newBase = converted.getFileName().toString().replace(".mkv", "");
        Path nfo = show.resolve(newBase + ".nfo");
        assertThat("the NFO follows the video", Files.exists(nfo), is(true));
        assertThat("and names the new codec", Files.readString(nfo), containsString("<video><codec>HEVC</codec>"));
        assertThat("audio codec is left alone", Files.readString(nfo), containsString("<audio><codec>aac</codec>"));
        assertThat("the thumbnail follows the video", Files.exists(show.resolve(newBase + "-thumb.jpg")), is(true));
        assertThat("an episode whose name merely starts the same stays", Files.exists(show.resolve("Show - 010 [H264AVC][BBBBBBBB].nfo")), is(true));
        assertThat("no temp file is left", Files.exists(show.resolve(".Show - 01 [H264AVC][AAAAAAAA].transcoding.mkv")), is(false));

        var mapping = mappings.get(ed2k(converted), Files.size(converted)).orElseThrow();
        assertThat(mapping.getOriginalEd2k(), is(ORIGINAL_ED2K));
        assertThat("the Kodi watcher finds the file under its new name",
                files.getAniDBFileData(ORIGINAL_ED2K, originalSize).orElseThrow().getFileName(), is(converted.getFileName().toString()));

        assertThat("a finished release is not queued again",
                jobs.enqueue(ORIGINAL_ED2K, originalSize, converted, TranscodeConfig.Existing.SKIP), is(false));
    }

    @Test
    public void aJobInterruptedAfterEncodingResumesWithoutEncodingAgain() throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = queuedEpisode();
        long originalSize = Files.size(source);
        TranscodeConfig config = config().build();
        MediaProber prober = new MediaProber("ffprobe");

        // State as a crash right after the ENCODED commit leaves it: verified temp file, mapping stored.
        Path temp = show.resolve(".Show - 01 [H264AVC][AAAAAAAA].transcoding.mkv");
        new Transcoder(config, prober, new FileHandler()).encode(source, prober.probe(source).orElseThrow(), temp).orElseThrow();
        String tempHash = ed2k(temp);
        mappings.save(tempHash, Files.size(temp), ORIGINAL_ED2K, originalSize, source.getFileName().toString());
        TranscodeJob job = jobs.get(ORIGINAL_ED2K, originalSize).orElseThrow();
        job.setPhase(Phase.ENCODED);
        job.setTempPath(temp.toString());
        job.setLocalEd2k(tempHash);
        job.setLocalSize(Files.size(temp));
        job.setLocalCrc32("cccccccc");
        job.setConvertedPath(show.resolve("Show - 01 [HEVC][CCCCCCCC].mkv").toString());
        jobs.save(job);

        // An impossible encoder proves the resume never runs ffmpeg again.
        assertThat(runner(config().videoArgs("-c:v does-not-exist").build()).runOnce(), is(true));

        TranscodeJob resumed = jobs.get(ORIGINAL_ED2K, originalSize).orElseThrow();
        assertThat(resumed.getLastError(), resumed.getPhase(), is(Phase.DONE));
        Path converted = show.resolve("Show - 01 [HEVC][CCCCCCCC].mkv");
        assertThat(ed2k(converted), is(tempHash));
        assertThat(Files.exists(show.resolve("Show - 01 [HEVC][CCCCCCCC].nfo")), is(true));
        assertThat(Files.exists(originals.resolve(source.getFileName())), is(true));
    }

    @Test
    public void aFailingEncodeIsRetriedUpToMaxAttemptsAndNeverTouchesTheSource() throws Exception {
        assumeTrue(ffmpegAvailable, "ffmpeg and ffprobe are needed for this test");
        Path source = queuedEpisode();
        long originalSize = Files.size(source);
        TranscodeRunner runner = runner(config().videoArgs("-c:v does-not-exist").maxAttempts(2).build());

        runner.runOnce();
        TranscodeJob afterFirst = jobs.get(ORIGINAL_ED2K, originalSize).orElseThrow();
        assertThat(afterFirst.getPhase(), is(Phase.PENDING));
        assertThat(afterFirst.getAttempts(), is(1));

        runner.runOnce();
        TranscodeJob afterSecond = jobs.get(ORIGINAL_ED2K, originalSize).orElseThrow();
        assertThat(afterSecond.getPhase(), is(Phase.FAILED));
        assertThat(afterSecond.getAttempts(), is(2));
        assertThat(runner.runOnce(), is(false));

        assertThat(Files.exists(source), is(true));
        assertThat(Files.exists(show.resolve(".Show - 01 [H264AVC][AAAAAAAA].transcoding.mkv")), is(false));
    }

    @Test
    public void aMissingSourceFailsAtOnce() throws Exception {
        jobs.enqueue(ORIGINAL_ED2K, 100, show.resolve("gone.mkv"), TranscodeConfig.Existing.SKIP);

        runner(config().maxAttempts(5).build()).runOnce();

        TranscodeJob job = jobs.get(ORIGINAL_ED2K, 100).orElseThrow();
        assertThat(job.getPhase(), is(Phase.FAILED));
        assertThat(job.getLastError(), containsString("does not exist"));
    }
}
