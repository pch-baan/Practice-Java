package concurrency.bloom.l3.StaleCache.production;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StaleCacheTest {

    @Autowired
    private BugPriceService bugService;

    @Autowired
    private FixedPriceService fixedService;

    @Test
    void bug_returns_stale_price_after_update() {
        // Setup: price = 100, prime cache
        bugService.updatePrice(1L, 100);
        bugService.getPrice(1L);            // cache miss → loads from DB → cache = {1: 100}

        // Action: admin updates price to 200
        bugService.updatePrice(1L, 200);    // DB = 200, cache NOT evicted (bug!)

        // Verify: cache still serves old value
        int served = bugService.getPrice(1L);   // cache HIT → 100 (stale!)

        System.out.println("[BUG]   DB=200 | Cache serves=" + served + " ← STALE (wrong!)");
        assertThat(served).isEqualTo(100);  // proves stale read — the bug exists
    }

    @Test
    void fix_returns_fresh_price_after_update() {
        // Setup: price = 100, prime cache (use different ID to isolate from bug test)
        fixedService.updatePrice(2L, 100);
        fixedService.getPrice(2L);              // cache miss → loads from DB → cache = {2: 100}

        // Action: admin updates price to 200
        fixedService.updatePrice(2L, 200);      // DB = 200, cache evicted ✅

        // Verify: next read fetches fresh from DB
        int served = fixedService.getPrice(2L); // cache MISS → loads from DB → 200

        System.out.println("[FIXED] DB=200 | Cache serves=" + served + " ← FRESH ✅");
        assertThat(served).isEqualTo(200);
    }
}
