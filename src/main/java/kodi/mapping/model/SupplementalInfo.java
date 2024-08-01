package kodi.mapping.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class SupplementalInfo {
    boolean replace;
    @Singular
    List<String> studios;
    @Singular
    List<String> genres;
    @Singular
    List<String> directors;
    @Singular
    List<String> credits;
    @Singular
    List<String> fanarts;

}
