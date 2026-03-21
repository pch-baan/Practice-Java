package concurrency.bloom.l3.StaleCache.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ❌ BUG: updatePrice() cập nhật DB nhưng KHÔNG evict cache.
 *
 * Kết quả: Sau khi update, getPrice() tiếp tục trả giá trị cũ từ cache
 * cho đến khi cache entry tự expire (nếu có TTL) hoặc restart app.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BugPriceService {

    private final PriceStore store;
    private final Map<Long, Integer> cache = new ConcurrentHashMap<>();

    public int getPrice(Long productId) {
        return cache.computeIfAbsent(productId, store::get);  // Cache-Aside
    }

    public void updatePrice(Long productId, int newPrice) {
        store.set(productId, newPrice);
        // ❌ cache.remove(productId) bị bỏ qua → cache trở thành stale
        log.info("[BUG] DB updated to {}. Cache still holds: {}", newPrice, cache.get(productId));
    }
}
