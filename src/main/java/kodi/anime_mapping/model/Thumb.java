package kodi.anime_mapping.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Thumb {
    String url;
    String dimension;
}
