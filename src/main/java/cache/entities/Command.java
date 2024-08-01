package cache.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.GUIDGenerator;
import org.hibernate.id.UUIDGenerator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Command {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    private UUID Id;

    @OneToMany(targetEntity = CommandParameter.class, cascade = CascadeType.ALL, mappedBy = "commandId")
    @Singular
    private List<CommandParameter> parameters;

    private String action;
    private String identifier;
    private Integer tag;
    private boolean needsLogin;
}
