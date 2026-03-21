package concurrency.bloom.l3.IdGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

/**
 * Bug Demo: Race condition khi dùng static counter để sinh ID.
 *
 * Pattern sai (educational only — không bao giờ làm trong production):
 *   static int nextOrderId = 1;
 *   int id = nextOrderId++;   ← read + write không atomic
 *
 * Race window:
 *   Thread A: read nextOrderId = 5 → [B chen vào] → write nextOrderId = 6 → id = 5
 *   Thread B: read nextOrderId = 5 →               → write nextOrderId = 6 → id = 5
 *   Kết quả: 2 order cùng nhận ID #5 → duplicate ID!
 *
 * Fix trong production: @GeneratedValue(IDENTITY) — DB sequence xử lý,
 * thread-safe across JVM instances, restarts, và deployments.
 * Xem production/ để chạy integration test chứng minh.
 */
public class IdGenerationBugDemo {

    static int nextOrderId = 1;

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 200;
        List<Thread> threads = new ArrayList<>();
        CyclicBarrier startGun = new CyclicBarrier(threadCount);

        // firstOwner: lưu customer nào nhận ID đó đầu tiên
        Map<Integer, String> firstOwner = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final String customer = "C" + i;
            threads.add(new Thread(() -> {
                try {
                    startGun.await();

                    int id = nextOrderId;               // read
                    Thread.yield();                     // mở rộng race window
                    nextOrderId = nextOrderId + 1;      // write

                    String prev = firstOwner.putIfAbsent(id, customer);
                    if (prev != null) {
                        System.out.println("BUG: Order #" + id
                                + " assigned to [" + prev + "] AND [" + customer + "] — DUPLICATE ID!");
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        System.out.println("---");
        System.out.println("Unique IDs issued : " + firstOwner.size() + " / Expected: " + threadCount);
        if (firstOwner.size() < threadCount) {
            System.out.println("BUG confirmed: " + (threadCount - firstOwner.size()) + " IDs bị duplicate.");
        } else {
            System.out.println("No bug caught this run — try again (race is non-deterministic).");
        }
        System.out.println("Fix: dùng @GeneratedValue(IDENTITY) — xem production/");
    }
}
