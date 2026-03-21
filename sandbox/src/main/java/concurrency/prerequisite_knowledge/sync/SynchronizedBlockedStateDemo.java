package concurrency.prerequisite_knowledge.sync;

/**
 * ← tách từ Demo 3
 * Demo: Quan sát Thread.State = BLOCKED bằng Thread.getState()
 *
 * Thread A giữ lock → Thread B + C cố vào synchronized → BLOCKED
 *
 * Chạy:
 *   mvn exec:java -pl sandbox -Dexec.mainClass="concurrency.prerequisite_knowledge.sync.SynchronizedBlockedStateDemo"
 */
public class SynchronizedBlockedStateDemo {

    static final Object SHARED_LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("════════════════════════════════════════");
        System.out.println("DEMO: Quan sát Thread.State = BLOCKED");
        System.out.println("════════════════════════════════════════");
        System.out.println();
        System.out.println("  [holder]          [blocked1]         [blocked2]");
        System.out.println("     │                   │                   │");
        System.out.println("  lock() ✅              │                   │");
        System.out.println("  giữ 3s                 │                   │");
        System.out.println("     │            lock() ─▶ BLOCKED   lock() ─▶ BLOCKED");
        System.out.println("     │              (xếp hàng chờ)      (xếp hàng chờ)");
        System.out.println("     │                   ║                   ║");
        System.out.println("  unlock() ✅            ║                   ║");
        System.out.println("     │                   ▼                   ║");
        System.out.println("     │            lock() ✅                  ║");
        System.out.println("     │            chạy xong                  ▼");
        System.out.println("     │            unlock()            lock() ✅");
        System.out.println("     │                                chạy xong");
        System.out.println();

        Thread holder = new Thread(() -> {
            synchronized (SHARED_LOCK) {
                System.out.println("[holder] Đang giữ lock...");
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                System.out.println("[holder] Thả lock");
            }
        }, "holder-thread");

        Thread blocked1 = new Thread(() -> {
            synchronized (SHARED_LOCK) { System.out.println("[blocked1] Vào được!"); }
        }, "blocked-thread-1");

        Thread blocked2 = new Thread(() -> {
            synchronized (SHARED_LOCK) { System.out.println("[blocked2] Vào được!"); }
        }, "blocked-thread-2");

        holder.start();
        Thread.sleep(200); // chờ holder lấy lock

        blocked1.start();
        blocked2.start();
        Thread.sleep(200); // chờ blocked threads thử vào và bị chặn

        System.out.printf("[holder]   state = %s%n", holder.getState());
        System.out.printf("[blocked1] state = %s  ← đây!%n", blocked1.getState());
        System.out.printf("[blocked2] state = %s  ← đây!%n", blocked2.getState());

        holder.join();
        blocked1.join();
        blocked2.join();

        System.out.println("\n════════════════════════════════════════");
        System.out.println("Kết luận: BLOCKED = thread muốn tiếp tục");
        System.out.println("nhưng bị chặn bởi synchronized lock đang bị chiếm");
        System.out.println("════════════════════════════════════════");
    }
}
