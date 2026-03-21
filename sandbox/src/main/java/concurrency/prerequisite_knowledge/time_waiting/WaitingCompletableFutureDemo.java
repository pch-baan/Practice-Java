package concurrency.prerequisite_knowledge.time_waiting;

import java.util.concurrent.*;

/**
 * Demo: Gọi external API chậm (10s) — so sánh có timeout vs không có timeout
 *
 * Scenario thực tế:
 *   Controller gọi external payment API
 *   → API chậm 10 giây
 *   → Nếu không có timeout: thread treo vĩnh viễn (WAITING)
 *   → Nếu có timeout 5s:    trả về 504 Gateway Timeout (TIMED_WAITING)
 *
 * Chạy:
 *   mvn exec:java -pl sandbox -Dexec.mainClass="concurrency.waiting.WaitingCompletableFutureDemo"
 */
public class WaitingCompletableFutureDemo {

    // Giả lập external API mất 10 giây
    static CompletableFuture<String> callPaymentApi() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("  [payment-api] Đang xử lý... (mất 10 giây)");
                Thread.sleep(10_000);
                return "PAYMENT_OK";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        });
    }

    // ─── DEMO 1: Không có timeout → WAITING → thread treo vĩnh viễn ───────────

    static void demo1_NoTimeout() throws InterruptedException {
        System.out.println("\n════════════════════════════════════════");
        System.out.println("DEMO 1: future.get() — KHÔNG có timeout");
        System.out.println("════════════════════════════════════════");
        System.out.println();
        System.out.println("  [Tomcat Thread]       [Payment API]");
        System.out.println("        │                     │");
        System.out.println("        │── future.get() ────▶│");
        System.out.println("        │                     │ đang xử lý...");
        System.out.println("        ║                     │ 10s... 20s... ∞");
        System.out.println("      WAITING                 │");
        System.out.println("   (không deadline)           │");
        System.out.println("        ║                     │");
        System.out.println("        X  ← thread leak, user chờ mãi");
        System.out.println();

        CompletableFuture<String> future = callPaymentApi();

        Thread callerThread = new Thread(() -> {
            try {
                System.out.println("[caller] Gọi future.get()... chờ không giới hạn");
                String result = future.get(); // → WAITING
                System.out.println("[caller] Kết quả: " + result);
            } catch (InterruptedException e) {
                System.out.println("[caller] Bị interrupt — buộc phải thoát");
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                System.out.println("[caller] Lỗi: " + e.getMessage());
            }
        }, "caller-no-timeout");

        callerThread.start();
        Thread.sleep(300);

        System.out.printf("[caller] state = %s  ← không biết bao giờ xong%n", callerThread.getState());
        System.out.println("  → Thread treo ở đây... user chờ mãi không có response");

        Thread.sleep(1000);
        System.out.println("[main] Buộc interrupt để giải phóng thread...");
        callerThread.interrupt();
        callerThread.join();
    }

    // ─── DEMO 2: Có timeout 5s → TIMED_WAITING → trả về 504 ────────────────────

    static void demo2_WithTimeout() throws InterruptedException {
        System.out.println("\n════════════════════════════════════════");
        System.out.println("DEMO 2: future.get(5, SECONDS) — CÓ timeout");
        System.out.println("════════════════════════════════════════");
        System.out.println();
        System.out.println("  [Tomcat Thread]       [Payment API]");
        System.out.println("        │                     │");
        System.out.println("        │── future.get(5s) ──▶│");
        System.out.println("        │                     │ đang xử lý...");
        System.out.println("        ║                     │");
        System.out.println("   TIMED_WAITING              │");
        System.out.println("    (deadline: 5s)            │");
        System.out.println("        ║                     │");
        System.out.println("      5s hết!                 │ (vẫn đang xử lý)");
        System.out.println("        │                     │");
        System.out.println("        │◀── TimeoutException─┘");
        System.out.println("        │");
        System.out.println("        └──▶ HTTP 504 Gateway Timeout");
        System.out.println();

        CompletableFuture<String> future = callPaymentApi();

        Thread callerThread = new Thread(() -> {
            try {
                System.out.println("[caller] Gọi future.get(5, SECONDS)... chờ tối đa 5 giây");
                String result = future.get(5, TimeUnit.SECONDS); // → TIMED_WAITING
                System.out.println("[caller] Kết quả: " + result);
            } catch (TimeoutException e) {
                future.cancel(true);
                System.out.println("[caller] ⏰ TimeoutException — API không trả lời trong 5s");
                System.out.println("[caller] 🚨 HTTP 504 Gateway Timeout");
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
            }
        }, "caller-with-timeout");

        callerThread.start();
        Thread.sleep(300);

        System.out.printf("[caller] state = %s  ← biết sẽ tự thoát sau 5s%n", callerThread.getState());
        System.out.println("  → Thread chờ có deadline, tự dậy khi hết giờ");

        callerThread.join();
    }

    public static void main(String[] args) throws InterruptedException {
        demo1_NoTimeout();
        demo2_WithTimeout();

        System.out.println("\n════════════════════════════════════════");
        System.out.println("Kết luận:");
        System.out.println("  future.get()           → WAITING       → treo vĩnh viễn ❌");
        System.out.println("  future.get(5, SECONDS) → TIMED_WAITING → 504 gracefully ✅");
        System.out.println("════════════════════════════════════════");
    }
}
