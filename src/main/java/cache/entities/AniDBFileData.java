package cache.entities;

import jakarta.persistence.*;
import kodi.common.UniqueId;
import kodi.nfo.model.Episode;
import kodi.nfo.model.Movie;
import kodi.nfo.model.StreamDetails;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import processing.tagsystem.TagSystemTags;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.time.LocalDateTime;
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

    @CreationTimestamp
    @Column(updatable = false)
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime updatedAt;


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


    @Transient
    public int aniDbAnimeId() {
        return Integer.parseInt(tags.get(TagSystemTags.AnimeId));
    }

    @Transient
    public int aniDbEpisodeId() {
        return Integer.parseInt(tags.get(TagSystemTags.EpisodeId));
    }

    public Movie.MovieBuilder toMovie() {
        return Movie.builder()
                .runtimeInSeconds(Integer.parseInt(tags.get(TagSystemTags.FileDuration)))
                .streamDetails(getStreamDetails())
                .uniqueId(UniqueId.AniDbFileId(Long.parseLong(tags.get(TagSystemTags.FileId))))
                .uniqueId(UniqueId.AniDbAnimeId(Long.parseLong(tags.get(TagSystemTags.AnimeId))))
                .uniqueId(UniqueId.AniDbEpisodeId(this.aniDbEpisodeId()))
                .premiered(LocalDate.ofInstant(Instant.ofEpochSecond(Long.parseLong(tags.get(TagSystemTags.EpisodeAirDate))), ZoneId.systemDefault()));
    }

    public Episode.EpisodeBuilder toEpisode() {
        return Episode.builder()
                .title(tags.get(TagSystemTags.EpisodeNameEnglish))
                .season(seasonNumber())
                .episode(episodeNumber())
                .runtimeInSeconds(Integer.parseInt(tags.get(TagSystemTags.FileDuration)))
                .streamDetails(getStreamDetails())
                .uniqueId(UniqueId.AniDbFileId(Long.parseLong(tags.get(TagSystemTags.FileId))))
                .uniqueId(UniqueId.AniDbAnimeId(Long.parseLong(tags.get(TagSystemTags.AnimeId))))
                .uniqueId(UniqueId.AniDbEpisodeId(this.aniDbEpisodeId()))
                .premiered(LocalDate.ofInstant(Instant.ofEpochSecond(Long.parseLong(tags.get(TagSystemTags.EpisodeAirDate))), ZoneId.systemDefault()));
    }

    private StreamDetails getStreamDetails() {
        val video = StreamDetails.Video.builder()
                .codec(tags.get(TagSystemTags.FileVideoCodec))
                .durationInSeconds(Integer.parseInt(tags.get(TagSystemTags.FileDuration)));
        val resolution = tags.get(TagSystemTags.FileVideoResolution);
        if (resolution != null) {
            val wxh = resolution.split("x");
            video.width(Integer.parseInt(wxh[0]));
            video.height(Integer.parseInt(wxh[1]));
        }
        return StreamDetails.builder()
                .audio(StreamDetails.Audio.builder()
                        .language(tags.get(TagSystemTags.FileAudioLanguage))
                        .build())
                .video(video.build())
                .subtitles(List.of(tags.get(TagSystemTags.FileSubtitleLanguage).split("'")))
                .build();
    }
}
