package aniAdd.startup.commands.anidb;

import lombok.val;
import picocli.CommandLine;
import processing.TagSystem;

import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(name = "tags",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Output result of tag system for testing")
public class TagsCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--movie"}, description = "Test movie naming", required = false)
    private boolean movie;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
        val tags = getExampleTagData(movie);
        val configuration = parent.getConfiguration();
        tags.put("BaseTVShowPath", configuration.getTvShowFolder());
        tags.put("BaseMoviePath", configuration.getMovieFolder());
        TagSystem.Evaluate(configuration.getTagSystemCode(), tags);
        val filename = tags.get("FileName");
        val pathname = tags.get("PathName");
        Logger.getGlobal().log(Level.INFO, STR."Filename: \{filename}");
        Logger.getGlobal().log(Level.INFO, STR."Pathname: \{pathname}");
        return 0;
    }

    static TreeMap<String, String> getExampleTagData(boolean movie) {
        TreeMap<String, String> tags = new TreeMap<String, String>();
        tags.put("ATr", "Suzumiya Haruhi no Yuuutsu (2009)");
        tags.put("ATe", "The Melancholy of Haruhi Suzumiya (2009)");
        tags.put("ATk", "涼宮ハルヒの憂鬱 (2009)");
        tags.put("ATs", "Suzumiya Haruhi no Yuuutsu (2009) Syn");
        tags.put("ATo", "haruhi2");
        tags.put("AYearBegin", "2009");
        tags.put("AYearEnd", "2010");
        tags.put("ACatList", "Clubs'Comedy'School Life'Seinen");

        tags.put("ETr", "Sasa no Ha Rhapsody");
        tags.put("ETe", "Bamboo Leaf Rhapsody");
        tags.put("ETk", "笹の葉ラプソディ");
        tags.put("EAirDate", "");

        tags.put("GTs", "a.f.k.");
        tags.put("GTl", "a.f.k. (Long)");


        tags.put("FCrc", "4a8cbc62");
        tags.put("FALng", "english'japanese'german");
        tags.put("FACodec", "AC3");
        tags.put("FSLng", "english'german");
        tags.put("FVCodec", "H264/AVC");
        tags.put("FVideoRes", "1920x1080");

        tags.put("FColorDepth", "");
        tags.put("FDuration", "1440");

        tags.put("AniDBFN", "Suzumiya_Haruhi_no_Yuuutsu_(2009)_-_01_-_The_Melancholy_of_Suzumiya_Haruhi_Part_1_-_[a.f.k.](32f2f4ea).avi");
        tags.put("CurrentFN", "[Chihiro]_Suzumiya_Haruhi_no_Yuutsu_(2009)_-_01_[848x480_H.264_AAC][7595C366].mkv");


        tags.put("EpNo", "1");
        if (movie) {
            tags.put("EpHiNo", "1");
            tags.put("EpCount", "1");
        } else {
            tags.put("EpHiNo", "150");
            tags.put("EpCount", "230");
        }
        tags.put("FId", "1");
        tags.put("AId", "2");
        tags.put("EId", "3");
        tags.put("GId", "4");
        tags.put("LId", "5");

        tags.put("OtherEps", "5'7");

        tags.put("Quality", "Very Good");
        tags.put("Source", "DVD");
        if (!movie) {
            tags.put("Type", "TV Series");
        } else {
            tags.put("Type", "Movie");
        }

        tags.put("Watched", "1");

        tags.put("Depr", "");
        tags.put("CrcOK", "1");
        tags.put("CrcErr", "0");
        tags.put("Depr", "");

        tags.put("Cen", "");
        tags.put("UnCen", "1");

        tags.put("Ver", "1");

        return tags;
    }
}
