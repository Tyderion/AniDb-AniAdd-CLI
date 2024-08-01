package cache.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "command_parameters")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommandParameter {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID Id;

    @Column()
    private long commandId;

    private String key;
    private String value;
}
