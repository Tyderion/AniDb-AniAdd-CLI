package kodi.anime_details.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.*;

import java.util.Set;

@Entity
@Builder
@Data
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class Creator {
    @Id
    int id;

    String name;

    @OneToMany(mappedBy = "anime")
    @ToString.Exclude
    Set<AnimeCreator> animes;
}
