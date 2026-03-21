package concurrency.bloom.l3.ReadModifyWrite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bug Demo: Read-Modify-Write Race Condition (Oversell)
 *
 * Kịch bản: Kho chỉ còn 1 sản phẩm. 50 thread cùng lúc kiểm tra và mua.
 *
 * Race window:
 *   Thread A: stock=1 → check OK → [Thread B chen vào] → stock-- → sold!
 *   Thread B: stock=1 → check OK →                    → stock-- → sold!
 *   Kết quả: 2 người mua được, kho âm hoặc không nhất quán.
 *
 * Fix: @Version (Optimistic Lock) — xem ProductService.java trong production/
 */
public class StockOversellBugDemo {

    static volatile int stock = 1;           // Chỉ còn 1 cái
    static AtomicInteger soldCount = new AtomicInteger(0);

    static void purchase(String customer) {
        if (stock >= 1) {                    // ← Thread A đọc: stock=1, OK
            Thread.yield();                  // ← Thread B chen vào đây, cũng đọc: stock=1, OK
            stock = stock - 1;              // ← Cả hai đều trừ → stock bị trừ 2 lần hoặc không nhất quán
            soldCount.incrementAndGet();
            System.out.println("SOLD: " + customer + " | stock=" + stock);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 50;
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier startGun = new CyclicBarrier(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String customer = "C" + i;
            threads.add(new Thread(() -> {
                try {
                    startGun.await();
                    purchase(customer);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        System.out.println("---");
        System.out.println("Initial stock : 1");
        System.out.println("Sold          : " + soldCount.get() + "  ← should be 1");
        System.out.println("Remaining     : " + stock + "  ← should be 0");
        if (soldCount.get() > 1) {
            System.out.println("BUG confirmed: OVERSELL! Sold " + soldCount.get() + " but only had 1 in stock.");
        } else {
            System.out.println("No bug caught this run — try again (race is non-deterministic).");
        }
    }
}
