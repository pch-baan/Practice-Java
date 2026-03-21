package annotation.Transactional.production;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tx_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
}
