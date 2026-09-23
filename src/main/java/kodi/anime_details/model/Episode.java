package kodi.anime_details.model;

import lombok.*;

import java.time.LocalDate;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Episode {
    int id;
    private LocalDate airDate;

    int voteCount;
    double rating;
}
