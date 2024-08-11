package aniAdd.startup.commands;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import processing.tagsystem.TagSystem;
import processing.tagsystem.TagSystemTags;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "tags",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Output result of tag system for testing")
public class TagsCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--movie"}, description = "Test movie naming", required = false)
    private boolean movie;

    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = false, scope = CommandLine.ScopeType.INHERIT)
    @Setter String configPath;

    @CommandLine.ParentCommand
    private CliCommand parent;

    @Override
    public Integer call() throws Exception {
        val tags = getExampleTagData(movie);
        val optionalConfig = parent.getConfiguration(true, configPath);
        if (optionalConfig.isEmpty() || optionalConfig.get().getTagSystemCode() == null
                || optionalConfig.get().getTagSystemCode().isEmpty()
                || optionalConfig.get().getTagSystemCode().isBlank()) {
            log.error("To test tags you must provide a non empty tagging system code");
            return 1;
        }
        val configuration = optionalConfig.get();
        tags.put(TagSystemTags.BaseTvShowPath, configuration.getTvShowFolder());
        tags.put(TagSystemTags.BaseMoviePath, configuration.getMovieFolder());
        val result = TagSystem.Evaluate(configuration.getTagSystemCode(), tags);
        val filename = result.FileName();
        val pathname = result.PathName();
        log.info(STR."Filename: \{filename}, Pathname: \{pathname}");
        return 0;
    }

    static Map<TagSystemTags, String> getExampleTagData(boolean movie) {
        Map<TagSystemTags, String> tags = new HashMap<>();
        tags.put(TagSystemTags.SeriesNameRomaji, "Suzumiya Haruhi no Yuuutsu (2009)");
        tags.put(TagSystemTags.SeriesNameEnglish, "The Melancholy of Haruhi Suzumiya (2009)");
        tags.put(TagSystemTags.SeriesNameKanji, "涼宮ハルヒの憂鬱 (2009)");
        tags.put(TagSystemTags.SeriesNameSynonyms, "Suzumiya Haruhi no Yuuutsu (2009) Syn");
        tags.put(TagSystemTags.SeriesNameOther, "haruhi2");
        tags.put(TagSystemTags.SeriesYearBegin, "2009");
        tags.put(TagSystemTags.SeriesYearEnd, "2010");
        tags.put(TagSystemTags.SeriesCategoryList, "Clubs'Comedy'School Life'Seinen");

        tags.put(TagSystemTags.EpisodeNameRomaji, "Sasa no Ha Rhapsody");
        tags.put(TagSystemTags.EpisodeNameEnglish, "Bamboo Leaf Rhapsody");
        tags.put(TagSystemTags.EpisodeNameKanji, "笹の葉ラプソディ");
        tags.put(TagSystemTags.EpisodeAirDate, "");

        tags.put(TagSystemTags.GroupNameShort, "a.f.k.");
        tags.put(TagSystemTags.GroupNameLong, "a.f.k. (Long)");


        tags.put(TagSystemTags.FileCrc, "4a8cbc62");
        tags.put(TagSystemTags.FileAudioLanguage, "english'japanese'german");
        tags.put(TagSystemTags.FileAudioCodec, "AC3");
        tags.put(TagSystemTags.FileSubtitleLanguage, "english'german");
        tags.put(TagSystemTags.FileVideoCodec, "H264/AVC");
        tags.put(TagSystemTags.FileVideoResolution, "1920x1080");

        tags.put(TagSystemTags.FileColorDepth, "");
        tags.put(TagSystemTags.FileDuration, "1440");

        tags.put(TagSystemTags.FileAnidbFilename, "Suzumiya_Haruhi_no_Yuuutsu_(2009)_-_01_-_The_Melancholy_of_Suzumiya_Haruhi_Part_1_-_[a.f.k.](32f2f4ea).avi");
        tags.put(TagSystemTags.FileCurrentFilename, "[Chihiro]_Suzumiya_Haruhi_no_Yuutsu_(2009)_-_01_[848x480_H.264_AAC][7595C366].mkv");


        tags.put(TagSystemTags.EpisodeNumber, "1");
        if (movie) {
            tags.put(TagSystemTags.EpisodeHiNumber, "1");
            tags.put(TagSystemTags.EpisodeCount, "1");
        } else {
            tags.put(TagSystemTags.EpisodeHiNumber, "150");
            tags.put(TagSystemTags.EpisodeCount, "230");
        }
        tags.put(TagSystemTags.FileId, "1");
        tags.put(TagSystemTags.AnimeId, "2");
        tags.put(TagSystemTags.EpisodeId, "3");
        tags.put(TagSystemTags.GroupId, "4");
        tags.put(TagSystemTags.MyListId, "5");

        tags.put(TagSystemTags.OtherEpisodes, "5'7");

        tags.put(TagSystemTags.Quality, "Very Good");
        tags.put(TagSystemTags.Source, "DVD");
        if (!movie) {
            tags.put(TagSystemTags.Type, "TV Series");
        } else {
            tags.put(TagSystemTags.Type, "Movie");
        }

        tags.put(TagSystemTags.Watched, "1");

        tags.put(TagSystemTags.Deprecated, "");
        tags.put(TagSystemTags.CrcOK, "1");
        tags.put(TagSystemTags.CrcError, "0");

        tags.put(TagSystemTags.Censored, "");
        tags.put(TagSystemTags.Uncensored, "1");

        tags.put(TagSystemTags.Version, "1");

        return tags;
    }
}
