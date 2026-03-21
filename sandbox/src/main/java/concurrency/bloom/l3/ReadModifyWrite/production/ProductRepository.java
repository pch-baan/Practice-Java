package concurrency.bloom.l3.ReadModifyWrite.production;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
