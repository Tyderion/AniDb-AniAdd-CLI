package cache.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.GUIDGenerator;
import org.hibernate.id.UUIDGenerator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
public class Command {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long Id;

    @OneToMany(targetEntity = CommandParameter.class)
    @JoinColumn(name = "commandId")
    @Singular
    private Set<CommandParameter> parameters;

    private String action;
    private String identifier;
    private Integer tag;
    private boolean needsLogin;
}
