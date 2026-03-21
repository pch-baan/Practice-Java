package concurrency.prerequisite_knowledge.sync;

import java.util.concurrent.*;

/**
 *  ← tách từ Demo 2
 * Demo: BLOCKED — synchronized sai chỗ gây bottleneck
 *
 * Thực tế Spring Boot:
 *   @Service class OrderService {
 *       public synchronized void updateInventory() { ... } // ← SAI
 *   }
 *   → Chỉ 1 thread xử lý được tại 1 thời điểm
 *   → Các thread khác: BLOCKED chờ method unlock
 *
 * Chạy:
 *   mvn exec:java -pl sandbox -Dexec.mainClass="concurrency.prerequisite_knowledge.sync.SynchronizedBottleneckDemo"
 */
public class SynchronizedBottleneckDemo {

    static int inventory = 100;

    // Giả lập @Service method dùng synchronized sai cách
    static synchronized void updateInventory(String requestName, int quantity) {
        System.out.printf("[%s] Vào updateInventory(), inventory hiện tại: %d%n",
                requestName, inventory);
        try {
            Thread.sleep(800); // giả lập xử lý lâu bên trong
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        inventory -= quantity;
        System.out.printf("[%s] Xong! inventory còn: %d%n", requestName, inventory);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("DEMO: synchronized Bottleneck — Thread.State = BLOCKED");
        System.out.println("5 request cùng gọi updateInventory() cùng lúc");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  [Order-1]          [Order-2]          [Order-3]   (Order-4,5 tương tự)");
        System.out.println("      │                    │                    │");
        System.out.println("   lock() ✅               │                    │");
        System.out.println("   xử lý 800ms             │                    │");
        System.out.println("      │             lock() ─▶ BLOCKED    lock() ─▶ BLOCKED");
        System.out.println("      │              (xếp hàng chờ)       (xếp hàng chờ)");
        System.out.println("      │                    ║                    ║");
        System.out.println("   unlock() ✅             ║                    ║");
        System.out.println("      │                    ▼                    ║");
        System.out.println("      │             lock() ✅                   ║");
        System.out.println("      │             xử lý 800ms                 ║");
        System.out.println("      │             unlock()                     ▼");
        System.out.println("      │                                   lock() ✅");
        System.out.println("      │                                   xử lý 800ms");
        System.out.println("      │                                   unlock() ...");
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 1; i <= 5; i++) {
            final String name = "Order-" + i;
            executor.submit(() -> {
                System.out.printf("[%s] Đang chờ vào updateInventory()... → BLOCKED%n", name);
                updateInventory(name, 1);
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n════════════════════════════════════════");
        System.out.println("Kết luận:");
        System.out.println("  → Chỉ 1 thread chạy được tại 1 thời điểm");
        System.out.println("  → 4 thread còn lại: BLOCKED xếp hàng");
        System.out.println("  → Throughput = 1 request / 800ms thay vì 5 request song song");
        System.out.println("════════════════════════════════════════");
    }
}
