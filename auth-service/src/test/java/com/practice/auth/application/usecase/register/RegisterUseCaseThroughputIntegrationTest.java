package com.practice.auth.application.usecase.register;

import com.practice.auth.application.dto.RegisterCommandDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Throughput benchmark — chứng minh async RabbitMQ (@TransactionalEventListener AFTER_COMMIT)
 * nhanh hơn đáng kể so với gửi email đồng bộ trong transaction khi tải cao.
 *
 * ── Lý thuyết (pool=10, THREADS=20, SMTP_SIMULATION=1000ms) ──────────────────
 *
 *   Async (RabbitMQ):
 *     ┌── DB work ~20ms ──┐           ← commit → publish AFTER_COMMIT (ngoài tx)
 *     DB connection: ~20ms/request
 *     20 requests / 10 pool = 2 batches × 20ms ≈ 40ms total
 *
 *   Sync (SMTP inside transaction):
 *     ┌── DB work ~20ms ──┬── SMTP 1000ms ──┐ ← commit
 *     DB connection: ~1020ms/request
 *     20 requests / 10 pool = 2 batches × 1020ms ≈ 2040ms total
 *     → 51× slower + connection pool exhaustion khi tải tăng
 *
 * ── Cơ chế đo ────────────────────────────────────────────────────────────────
 *   CountDownLatch(1) startLatch  → 20 thread xuất phát CÙNG LÚC (barrier)
 *   CountDownLatch(20) doneLatch  → đo wall-clock time khi tất cả xong
 *   Pool=10 → 10 req vào Batch 1, 10 req CHỜ → vào Batch 2 sau khi Batch 1 trả connection
 *
 * @see RegisterUseCaseAsyncVsSyncIntegrationTest correctness tests (Fault Isolation, No Ghost Message)
 */
class RegisterUseCaseThroughputIntegrationTest extends RegisterUseCaseIntegrationTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Throughput: Async vs Sync email in-transaction
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Throughput - pool=10, 20 concurrent] Sync email-in-transaction chậm hơn async RabbitMQ ít nhất 1000ms")
    void execute_concurrent_syncEmailInTransactionSlowerThanAsync() throws InterruptedException {
        final int  THREADS   = 20; // 20 khách hàng (concurrent requests) = 2× pool → 2 batches
        final int  POOL      = 10; // 10 quầy thu ngân (DB connections) — khớp pool thực tế
        final long SMTP_MS   = SyncEmailInTransactionSimulator.SMTP_SIMULATION_MS;
        //
        // Tính toán ngưỡng lý thuyết:
        //   Async:  DB work ~20ms/req × 20 req / 10 pool = 40ms minimum
        //   Sync:   (DB 20ms + SMTP 1000ms)/req × 20 / 10 = 2040ms minimum
        //   Diff:   20 req × 1000ms SMTP / 10 pool = 2000ms lý thuyết → assert >= 1000ms (50% margin)
        final long minExpectedDiffMs = (long) THREADS / POOL * SMTP_MS / 2; // = 1000ms

        // ─── Phase A: Async (thiết kế hiện tại — SpyPublisher 0ms, publish AFTER_COMMIT) ───
        log.info("▶ [THROUGHPUT] Phase A — Async RabbitMQ: {} concurrent, pool={}",
                THREADS, POOL);
        log.info("  DB connection held: ~20ms/request (chỉ DB work, publish xảy ra SAU commit)");
        log.info("  Theoretical: {} req × ~20ms / {} pool = ~{}ms minimum",
                THREADS, POOL, THREADS * 20 / POOL);

        long asyncMs = runConcurrentRegister(THREADS, "async", i ->
                registerUseCase.execute(
                        new RegisterCommandDto("async_u" + i, "async" + i + "@test.com", "Secret@123"))
        );
        log.info("  ✔ Async total wall time: {}ms", asyncMs);

        // Cleanup giữa 2 phase (không reset spy — vẫn theo dõi publish count)
        tokenJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        // ─── Phase B: Sync email-in-transaction (mô phỏng thiết kế sai) ────────────
        log.info("▶ [THROUGHPUT] Phase B — Sync email-in-tx: {} concurrent, pool={}", THREADS, POOL);
        log.info("  DB connection held: ~{}ms/request (DB 20ms + SMTP {}ms inside tx)", 20 + SMTP_MS, SMTP_MS);
        log.info("  Theoretical: {} req × ~{}ms / {} pool = ~{}ms minimum",
                THREADS, 20 + SMTP_MS, POOL, THREADS * (20 + SMTP_MS) / POOL);

        long syncMs = runConcurrentRegister(THREADS, "sync", i ->
                syncEmailSimulator.register("sync_u" + i, "sync" + i + "@test.com")
        );
        log.info("  ✔ Sync total wall time: {}ms", syncMs);

        // ─── Kết quả ─────────────────────────────────────────────────────────────
        long diffMs = syncMs - asyncMs;
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  Phase A  Async (RabbitMQ after-commit):  {}ms", asyncMs);
        log.info("  Phase B  Sync  (SMTP inside tx, {}ms ea): {}ms", SMTP_MS, syncMs);
        log.info("  Diff     sync - async:                    {}ms", diffMs);
        log.info("  Lý thuyết added latency: {} req × {}ms / {} pool = {}ms",
                THREADS, SMTP_MS, POOL, THREADS * SMTP_MS / POOL);
        log.info("  Assert:  diff >= {}ms (50%% của lý thuyết)", minExpectedDiffMs);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        org.assertj.core.api.Assertions.assertThat(diffMs)
                .as("Sync email-in-transaction PHẢI chậm hơn async ít nhất %dms "
                        + "(lý thuyết: %d requests x %dms SMTP / %d pool = %dms added latency). "
                        + "asyncMs=%dms, syncMs=%dms",
                        minExpectedDiffMs, THREADS, SMTP_MS, POOL, THREADS * SMTP_MS / POOL,
                        asyncMs, syncMs)
                .isGreaterThanOrEqualTo(minExpectedDiffMs);

        log.info("  ✔ Throughput xác nhận: async nhanh hơn sync {}ms (assert >= {}ms)",
                diffMs, minExpectedDiffMs);
    }
}
