package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Builder
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Episode {
    @Id
    @Column(nullable = false)
    int id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn
    Anime anime;

    @Column(columnDefinition = "DATE")
    private LocalDate airDate;

    int voteCount;
    double rating;
}
