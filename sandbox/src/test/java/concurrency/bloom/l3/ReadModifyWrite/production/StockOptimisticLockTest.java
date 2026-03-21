package concurrency.bloom.l3.ReadModifyWrite.production;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockOptimisticLockTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void concurrent_purchase_noOversell_with_optimistic_lock() throws InterruptedException {
        // Setup: 1 sản phẩm, stock = 1
        Product product = productRepository.save(Product.builder()
                .name("Limited Edition Sneaker")
                .stock(1)
                .build());
        Long productId = product.getId();

        int threadCount = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier startGun = new CyclicBarrier(threadCount);

        for (int i = 0; i < threadCount; i++) {
            threads.add(new Thread(() -> {
                try {
                    startGun.await();
                    try {
                        boolean bought = productService.purchase(productId);
                        if (bought) successCount.incrementAndGet();
                        else rejectedCount.incrementAndGet();  // out of stock
                    } catch (Exception e) {
                        // ObjectOptimisticLockingFailureException — version conflict
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        Product finalProduct = productRepository.findById(productId).orElseThrow();

        System.out.println("[PRODUCTION] Purchased: " + successCount.get()
                + " | Rejected (conflict/out-of-stock): " + rejectedCount.get());
        System.out.println("[PRODUCTION] Final stock: " + finalProduct.getStock());
        System.out.println("[PRODUCTION] Invariant: purchased(" + successCount.get()
                + ") + remaining(" + finalProduct.getStock() + ") = " + (successCount.get() + finalProduct.getStock())
                + " == initial stock (1)");

        // Invariant không bao giờ được vi phạm:
        // Số lượng đã bán + số lượng còn lại = số lượng ban đầu
        assertThat(successCount.get() + finalProduct.getStock()).isEqualTo(1);
        assertThat(finalProduct.getStock()).isGreaterThanOrEqualTo(0);
    }
}
