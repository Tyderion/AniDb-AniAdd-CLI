package kodi.anime_details.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Creator {
    @Id
    @Column(nullable = false)
    int id;

    String name;

    String picture;

    Type type;

    public enum Type {
        ORIGINAL_WORK, DIRECTION, CHARACTER_DESIGNER, ANIMATION_WORK;
    }
}
