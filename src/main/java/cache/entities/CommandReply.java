package cache.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CommandReply {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String Id;



}
