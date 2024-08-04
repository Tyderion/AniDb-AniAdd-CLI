package cache.entities;

import jakarta.persistence.*;
import kodi.common.UniqueId;
import kodi.nfo.Episode;
import lombok.*;
import processing.tagsystem.TagSystemTags;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AniDBFileData.AniDBFileId.class)
public class AniDBFileData {
    @Id
    @Column(nullable = false)
    @NonNull
    private String ed2k;

    @Id
    @Column(nullable = false)
    private long size;

    @Column(nullable = false, unique = true)
    @NonNull
    private String fileName;

    @Column(nullable = false)
    @NonNull
    private String folderName;

    @Singular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(joinColumns = {
            @JoinColumn(name = "file_ed2k", nullable = false, referencedColumnName = "ed2k"),
            @JoinColumn(name = "file_size", nullable = false, referencedColumnName = "size")})
    private Map<TagSystemTags, String> tags;


    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class AniDBFileId {
        private String ed2k;
        private long size;
    }

    @Transient
    public int seasonNumber() {
        val epNo = tags.get(TagSystemTags.EpisodeNumber);
        if (epNo.matches("[0-9]+")) {
            return 1;
        } else {
            return 0;
        }
    }

    @Transient
    public int episodeNumber() {
        val epNo = tags.get(TagSystemTags.EpisodeNumber);
        if (epNo.matches("[0-9]+")) {
            return Integer.parseInt(epNo);
        } else {
            return epNo.replaceAll("[^0-9]", "").isEmpty() ? 0 : Integer.parseInt(epNo.replaceAll("[^0-9]", ""));
        }
    }

    public Episode.EpisodeBuilder toEpisode() {
        val video = Episode.Video.builder()
                .codec(tags.get(TagSystemTags.FileVideoCodec))
                .durationInSeconds(Integer.parseInt(tags.get(TagSystemTags.FileDuration)));
        val resolution = tags.get(TagSystemTags.FileVideoResolution);
        if (resolution != null) {
            val wxh = resolution.split("x");
            video.width(Integer.parseInt(wxh[0]));
            video.height(Integer.parseInt(wxh[1]));
        }
        val streamDetails = Episode.StreamDetails.builder()
                .audio(Episode.Audio.builder()
                        .language(tags.get(TagSystemTags.FileAudioLanguage))
                        .build())
                .video(video.build())
                .subtitles(List.of(tags.get(TagSystemTags.FileSubtitleLanguage).split("'")))
                .build();

        val epNo = tags.get(TagSystemTags.EpisodeNumber);
        int episodeNumber = 0;
        int season = 1;
        if (epNo.matches("[0-9]+")) {
            episodeNumber = Integer.parseInt(epNo);
        } else {
            episodeNumber = Integer.parseInt(epNo.replaceAll("[^0-9]", ""));
            season = 0;
        }
        return Episode.builder()
                .title(tags.get(TagSystemTags.EpisodeNameEnglish))
                .plot("TODO TVDB")
                .season(season)
                .episode(episodeNumber)
                .runtimeInSeconds(Integer.parseInt(tags.get(TagSystemTags.FileDuration)))
                .watched(Boolean.parseBoolean(tags.get(TagSystemTags.Watched)))
                .streamDetails(streamDetails)
                .uniqueId(UniqueId.AniDbFileId(Long.parseLong(tags.get(TagSystemTags.FileId))))
                .uniqueId(UniqueId.AniDbAnimeId(Long.parseLong(tags.get(TagSystemTags.AnimeId))))
                .uniqueId(UniqueId.AniDbEpisodeId(Long.parseLong(tags.get(TagSystemTags.EpisodeId))))
                .premiered(LocalDate.ofInstant(Instant.ofEpochSecond(Long.parseLong(tags.get(TagSystemTags.EpisodeAirDate))), ZoneId.systemDefault()));

    }
}
