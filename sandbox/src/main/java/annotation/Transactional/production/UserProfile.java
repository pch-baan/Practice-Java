package annotation.Transactional.production;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tx_user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK trỏ về users.id.
     * Nếu @Transactional rollback → cả user lẫn profile đều bị hủy,
     * FK constraint không bao giờ bị vi phạm.
     */
    private Long userId;

    private String bio;
}
