package kodi.anime_details.model;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {
    private int id;

    String name;
    @ToString.Exclude
    String description;
    int weight;
}
