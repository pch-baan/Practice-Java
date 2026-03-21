package concurrency.bloom.l3.ReadModifyWrite.production;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int stock;

    /**
     * @Version là chìa khóa của Optimistic Locking.
     *
     * Khi save, JPA tự thêm điều kiện:
     *   UPDATE products SET stock=?, version=N+1 WHERE id=? AND version=N
     *
     * Nếu version đã bị thay đổi bởi transaction khác trước đó:
     *   → 0 rows updated → JPA throw ObjectOptimisticLockingFailureException
     *   → Caller phải handle: retry hoặc báo lỗi cho user.
     */
    @Version
    private Long version;
}
