package transcode;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class MediaProber {
    private final String ffprobePath;
    private final Gson gson = new Gson();

    public Optional<MediaInfo> probe(Path file) {
        val command = List.of(ffprobePath, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", file.toString());
        try {
            val process = new ProcessBuilder(command).redirectErrorStream(false).start();
            String output;
            try (val stdout = process.getInputStream()) {
                output = new String(stdout.readAllBytes());
            }
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                log.error(STR."ffprobe timed out for \{file}");
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                log.error(STR."ffprobe exited with \{process.exitValue()} for \{file}");
                return Optional.empty();
            }
            return parse(output, file);
        } catch (Exception e) {
            log.error(STR."Could not probe \{file}: \{e.getMessage()}. Is '\{ffprobePath}' on the PATH?");
            return Optional.empty();
        }
    }

    private Optional<MediaInfo> parse(String json, Path file) {
        val root = gson.fromJson(json, JsonObject.class);
        if (root == null || !root.has("streams")) {
            log.error(STR."ffprobe returned no streams for \{file}");
            return Optional.empty();
        }
        String videoCodec = null;
        String videoProfile = null;
        val audioCodecs = new ArrayList<String>();
        int video = 0, audio = 0, subtitle = 0, attachment = 0;
        double streamDuration = 0;
        for (val element : root.getAsJsonArray("streams")) {
            val stream = element.getAsJsonObject();
            val type = stringOrNull(stream, "codec_type");
            val codec = stringOrNull(stream, "codec_name");
            streamDuration = Math.max(streamDuration, doubleOrZero(stream, "duration"));
            switch (type == null ? "" : type) {
                case "video" -> {
                    if (videoCodec == null) {
                        videoCodec = codec;
                        videoProfile = stringOrNull(stream, "profile");
                    }
                    video++;
                }
                case "audio" -> {
                    audioCodecs.add(codec);
                    audio++;
                }
                case "subtitle" -> subtitle++;
                case "attachment" -> attachment++;
                default -> log.debug(STR."Ignoring stream of type '\{type}' in \{file}");
            }
        }
        val format = root.getAsJsonObject("format");
        val duration = format == null || doubleOrZero(format, "duration") == 0 ? streamDuration : doubleOrZero(format, "duration");
        val size = format == null ? 0L : (long) doubleOrZero(format, "size");
        val bitRate = format == null ? 0L : (long) doubleOrZero(format, "bit_rate");
        return Optional.of(MediaInfo.builder()
                .videoCodec(videoCodec)
                .videoProfile(videoProfile)
                .bitRateKbps(bitRate / 1000)
                .audioCodecs(List.copyOf(audioCodecs))
                .durationSeconds(duration)
                .sizeInBytes(size == 0 ? file.toFile().length() : size)
                .videoStreams(video)
                .audioStreams(audio)
                .subtitleStreams(subtitle)
                .attachmentStreams(attachment)
                .build());
    }

    private static String stringOrNull(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static double doubleOrZero(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
