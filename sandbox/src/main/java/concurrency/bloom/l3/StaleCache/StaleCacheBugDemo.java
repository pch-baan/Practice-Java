package concurrency.bloom.l3.StaleCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bug Demo: Stale Cache — Cache và DB mất đồng bộ.
 *
 * Pattern: Cache-Aside (Lazy Loading)
 *   Read  → check cache → cache miss → read DB → put vào cache → return
 *   Write → update DB → [QUÊN evict cache] → cache giữ giá trị cũ
 *
 * Race window:
 *   1. Client A đọc price → cache miss → load từ DB (100) → cache = {1: 100}
 *   2. Admin update price → DB = 200, nhưng cache vẫn = {1: 100}  ← BUG
 *   3. Client B đọc price → cache HIT → nhận 100 (STALE, sai!)
 *
 * Hậu quả thực tế:
 *   - User thấy giá cũ dù admin đã cập nhật
 *   - Sau khi flash sale kết thúc, một số user vẫn thấy giá sale cũ
 *   - Data inconsistency kéo dài đến khi cache expire (có thể vài giờ)
 */
public class StaleCacheBugDemo {

    static final Map<Long, Integer> db    = new ConcurrentHashMap<>();
    static final Map<Long, Integer> cache = new ConcurrentHashMap<>();

    static int getPrice(long id) {
        return cache.computeIfAbsent(id, db::get);  // Cache-Aside read
    }

    static void bugUpdate(long id, int newPrice) {
        db.put(id, newPrice);
        // ← cache.remove(id) bị thiếu!
    }

    static void fixedUpdate(long id, int newPrice) {
        db.put(id, newPrice);
        cache.remove(id);   // evict → next read fetch fresh từ DB
    }

    public static void main(String[] args) {
        long id = 1L;

        // ──── BUG scenario ────
        db.put(id, 100);
        getPrice(id);                 // prime cache → cache = {1: 100}
        bugUpdate(id, 200);           // DB = 200, cache vẫn = {1: 100}
        System.out.println("[BUG] DB=200, served=" + getPrice(id) + " ← STALE");

        // ──── FIX scenario ────
        cache.clear();
        db.put(id, 100);
        getPrice(id);                 // prime cache
        fixedUpdate(id, 200);         // DB = 200, cache evicted
        System.out.println("[FIX] DB=200, served=" + getPrice(id) + " ← FRESH");
    }
}
