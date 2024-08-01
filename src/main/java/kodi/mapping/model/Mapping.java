package kodi.mapping.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder
@Value
public class Mapping {
    int aniDbSeason;
    int tvDbSeason;
    Integer start;
    Integer end;
    Integer offset;
    @Singular
    Map<Integer, List<Integer>> mappings;
}
