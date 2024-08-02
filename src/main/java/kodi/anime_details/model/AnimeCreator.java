package kodi.anime_details.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnimeCreator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Anime anime;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn
    @EqualsAndHashCode.Exclude
    Creator creator;

    Type type;

    public enum Type {
        ORIGINAL_WORK, DIRECTION, CHARACTER_DESIGNER;
    }
}
