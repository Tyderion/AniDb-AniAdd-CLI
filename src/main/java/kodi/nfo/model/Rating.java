package kodi.nfo.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Rating {
    int voteCount;
    double rating;
    String name;
    int max;
}
