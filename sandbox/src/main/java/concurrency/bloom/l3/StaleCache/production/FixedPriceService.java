package concurrency.bloom.l3.StaleCache.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ FIXED: Cache-Aside với eviction đúng chỗ.
 *
 * Pattern: Write-Invalidate
 *   update DB → evict cache → next read sẽ fetch fresh từ DB
 *
 * Trong Spring thực tế, pattern này thường được viết bằng annotation:
 *   @CacheEvict(value = "prices", key = "#productId")
 *
 * Lưu ý quan trọng — "stale put" race vẫn có thể xảy ra:
 *   T1: cache miss → read DB (100) ──────────────────────── put(100) vào cache
 *   T2:                           update DB (200) + evict ─────────────────────
 *   Kết quả: T1 put giá trị cũ AFTER evict → cache lại stale!
 *
 * Fix cho race này: dùng version/timestamp hoặc distributed lock.
 * Với TTL ngắn (vài giây) thì acceptable trong hầu hết hệ thống.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixedPriceService {

    private final PriceStore store;
    private final Map<Long, Integer> cache = new ConcurrentHashMap<>();

    public int getPrice(Long productId) {
        return cache.computeIfAbsent(productId, store::get);
    }

    public void updatePrice(Long productId, int newPrice) {
        store.set(productId, newPrice);
        cache.remove(productId);    // ✅ evict → next getPrice() loads fresh từ DB
        log.info("[FIXED] DB updated to {} and cache evicted for product #{}", newPrice, productId);
    }
}
