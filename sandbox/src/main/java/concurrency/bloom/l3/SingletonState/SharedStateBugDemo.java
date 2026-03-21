package concurrency.bloom.l3.SingletonState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

/**
 * Bug Demo: Mutable state trong Singleton bị shared giữa các thread.
 *
 * Hình dung @Service trong Spring như class này:
 *   - Chỉ có 1 instance duy nhất trong toàn bộ ứng dụng
 *   - Mọi request (thread) đều dùng chung 1 instance đó
 *   - ArrayList.add() KHÔNG thread-safe → lost updates, exception
 *
 * Trong Spring thực tế:
 *   Request 1 (User A): service.submit("A's report")  ─┐
 *   Request 2 (User B): service.submit("B's report")  ─┤→ cùng ArrayList!
 *   Request 3 (User C): service.submit("C's report")  ─┘
 *
 * Hậu quả:
 *   1. Data corruption: ArrayList.add() không atomic → size < expected
 *   2. Data leak: User A thấy report của User B
 *   3. Exception: ArrayIndexOutOfBoundsException khi resize internal array
 */
public class SharedStateBugDemo {

    // Giả lập @Service singleton — 1 instance dùng chung
    static final List<String> sharedList = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 200;
        CyclicBarrier startGun = new CyclicBarrier(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String item = "item-" + i;
            threads.add(new Thread(() -> {
                try {
                    startGun.await();
                    sharedList.add(item);   // ArrayList.add() — NOT thread-safe!
                } catch (Exception e) {
                    // ArrayIndexOutOfBoundsException xảy ra ở đây khi race condition
                    System.out.println("EXCEPTION during add: " + e.getClass().getSimpleName());
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join();

        System.out.println("---");
        System.out.println("Expected size : " + threadCount);
        System.out.println("Actual size   : " + sharedList.size());
        if (sharedList.size() < threadCount) {
            System.out.println("BUG confirmed: Lost " + (threadCount - sharedList.size()) + " items due to race condition.");
        } else {
            System.out.println("No bug caught this run — but the code is still WRONG. Try again.");
        }
    }
}
