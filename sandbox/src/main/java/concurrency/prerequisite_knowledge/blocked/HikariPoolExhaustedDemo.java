package concurrency.prerequisite_knowledge.blocked;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo: BLOCKED — HikariCP connection pool bị cạn kiệt
 *
 * Thực tế Spring Boot:
 *   spring.datasource.hikari.maximum-pool-size=10 (mặc định)
 *   → Chỉ 10 thread được cầm connection cùng lúc
 *   → Thread thứ 11 trở đi: BLOCKED chờ connection trả về
 *
 * Chạy:
 *   mvn exec:java -pl sandbox -Dexec.mainClass="concurrency.blocked.HikariPoolExhaustedDemo"
 */
public class HikariPoolExhaustedDemo {

    // Giả lập connection pool với Semaphore — đúng cơ chế HikariCP dùng bên trong
    static final int POOL_SIZE = 3;
    static final Semaphore connectionPool = new Semaphore(POOL_SIZE, true);
    static final AtomicInteger inUse = new AtomicInteger(0);

    // HikariCP mặc định timeout sau 30s — ở đây dùng 600ms để demo nhanh
    // Query mất 1000ms > timeout 600ms → request xếp sau sẽ bị timeout
    static final long CONNECTION_TIMEOUT_MS = 600;

    static synchronized void log(String name, String msg) {
        System.out.printf("[%-11s] %s%n", name, msg);
    }

    static void handleRequest(String requestName) {
        boolean acquired = false;
        try {
            acquired = connectionPool.tryAcquire(50, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log(requestName, "⏳ BLOCKED   — pool đầy (" + inUse.get() + "/" + POOL_SIZE + "), chờ tối đa " + CONNECTION_TIMEOUT_MS + "ms...");

                acquired = connectionPool.tryAcquire(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log(requestName, "💥 TIMEOUT   — chờ quá " + CONNECTION_TIMEOUT_MS + "ms, không lấy được connection!");
                    log(requestName, "🚨 HTTP 500  — Unable to acquire JDBC Connection [ConnectionTimeoutException]");
                    return;
                }
            }

            int using = inUse.incrementAndGet();
            log(requestName, "✅ Lấy được  — đang query DB...   (đang dùng: " + using + "/" + POOL_SIZE + ")");

            Thread.sleep(1000); // query DB mất 1000ms

            int after = inUse.decrementAndGet();
            log(requestName, "🔓 Trả lại   — nhả connection     (đang dùng: " + after + "/" + POOL_SIZE + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired) connectionPool.release();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("════════════════════════════════════════");
        System.out.println("DEMO: HikariCP Connection Pool Exhausted + Timeout");
        System.out.printf("  Pool size    = %d slot%n", POOL_SIZE);
        System.out.printf("  Query time   = 1000ms (giả lập DB chậm)%n");
        System.out.printf("  Timeout      = %dms  (hikari.connection-timeout)%n", CONNECTION_TIMEOUT_MS);
        System.out.println("  Wave 1 (t=0ms)    : Request 1-6  → 1,2,3 vào được | 4,5,6 timeout HTTP 500");
        System.out.println("  Wave 2 (t=1100ms) : Request 7-9  → 1,2,3 vừa trả xong → 7,8,9 vào được");
        System.out.println("════════════════════════════════════════");

        ExecutorService executor = Executors.newFixedThreadPool(9);

        System.out.println("\n--- Wave 1: 6 request cùng lúc (t=0ms) ---");
        for (int i = 1; i <= 6; i++) {
            final String name = "Request-" + i;
            executor.submit(() -> handleRequest(name));
        }

        Thread.sleep(1100);

        System.out.println("\n--- Wave 2: 3 request mới (t=1100ms) — pool vừa trống ---");
        for (int i = 7; i <= 9; i++) {
            final String name = "Request-" + i;
            executor.submit(() -> handleRequest(name));
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n════════════════════════════════════════");
        System.out.println("Kết luận: pool cạn → thread BLOCKED → quá timeout → HTTP 500");
        System.out.println("Fix: tăng pool-size hoặc giảm query time");
        System.out.println("════════════════════════════════════════");
    }
}
