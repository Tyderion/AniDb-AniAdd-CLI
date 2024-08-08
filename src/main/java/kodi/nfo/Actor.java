package kodi.nfo;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Actor {
    String name;
    String role;
    int order;
    String thumb;
}