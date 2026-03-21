package concurrency.bloom.l3.ReadModifyWrite.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository repo;

    /**
     * Mua 1 sản phẩm.
     *
     * Thread-safety được đảm bảo bởi @Version trên Product:
     *   - Không cần synchronized, không cần lock thủ công
     *   - JPA tự generate: UPDATE ... WHERE id=? AND version=?
     *   - Nếu version conflict → ObjectOptimisticLockingFailureException
     *   - Caller tự quyết: retry, hoặc trả HTTP 409 Conflict cho client
     */
    @Transactional
    public boolean purchase(Long productId) {
        Product product = repo.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (product.getStock() <= 0) {
            log.info("[PRODUCTION] Product #{} — out of stock", productId);
            return false;
        }

        product.setStock(product.getStock() - 1);
        repo.save(product);
        log.info("[PRODUCTION] Product #{} purchased. Remaining: {}", productId, product.getStock());
        return true;
    }
}
