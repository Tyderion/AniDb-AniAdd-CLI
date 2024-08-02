package kodi.anime_details.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.*;

import java.util.Set;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {
    @Id
    @Column(nullable = false)
    private int id;

    String name;
    @ToString.Exclude
    String description;
}
