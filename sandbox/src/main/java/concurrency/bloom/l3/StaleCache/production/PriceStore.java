package concurrency.bloom.l3.StaleCache.production;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giả lập tầng DB (trong thực tế đây là JpaRepository hoặc gọi external service).
 */
@Component
public class PriceStore {

    private final Map<Long, Integer> db = new ConcurrentHashMap<>();

    public void set(Long id, int price) {
        db.put(id, price);
    }

    public int get(Long id) {
        return db.getOrDefault(id, 0);
    }
}
