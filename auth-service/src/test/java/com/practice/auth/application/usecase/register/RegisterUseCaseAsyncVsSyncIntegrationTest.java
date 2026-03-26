package com.practice.auth.application.usecase.register;

import com.practice.auth.application.dto.RegisterCommandDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correctness tests — chứng minh thiết kế async RabbitMQ (@TransactionalEventListener AFTER_COMMIT)
 * đúng đắn hơn gửi email đồng bộ trong transaction theo 2 khía cạnh:
 *
 * ── 1. FAULT ISOLATION ────────────────────────────────────────────────────────
 *
 *   Sync email TRONG transaction (cách thiết kế sai):
 *
 *     ┌── @Transactional ────────────────────────────────────────────┐
 *     │  createUser()   ← DB write                                   │
 *     │  saveToken()    ← DB write                                   │
 *     │  sendEmail()    ← SMTP  ←── NẾU FAIL → ROLLBACK toàn bộ      │
 *     └──────────────────────────────────────────────────────────────┘
 *     → Email server lỗi → ROLLBACK → user KHÔNG được tạo
 *     → DATA LOSS vì notification layer kéo DB layer xuống theo
 *
 *   Async RabbitMQ (thiết kế hiện tại):
 *
 *     ┌── @Transactional ──────────────────────────────────────────────┐
 *     │  createUser()         ← DB write                               │
 *     │  saveToken()          ← DB write                               │
 *     │  publishEvent()       ← chỉ QUEUE event, không gửi ngay        │
 *     └────────────────────────────────────────────────────────────────┘
 *               └── COMMIT ──→ @TransactionalEventListener(AFTER_COMMIT)
 *                              → publish to RabbitMQ
 *                                ←── NẾU FAIL → DB đã committed, user an toàn ✓
 *                                    Spring (6.1+) isolates listener failure từ caller ✓
 *
 * ── 2. NO GHOST MESSAGE ───────────────────────────────────────────────────────
 *
 *   Sync email (cách sai):  sendEmail() thành công → rồi DB rollback (lỗi khác)
 *     → user nhận email nhưng account không tồn tại ← ghost message nguy hiểm
 *
 *   @TransactionalEventListener(AFTER_COMMIT):
 *     publishEvent() chỉ QUEUE event → DB rollback → event bị HỦY (không bao giờ fire)
 *     → SpyPublisher.publish() KHÔNG được gọi ← zero ghost messages ✓
 *
 * @see RegisterUseCaseThroughputIntegrationTest throughput benchmark (pool=10, 20 concurrent)
 */
class RegisterUseCaseAsyncVsSyncIntegrationTest extends RegisterUseCaseIntegrationTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — Fault Isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Fault Isolation] Publisher fail (RabbitMQ down) → DB đã commit, user + token vẫn tồn tại")
    void execute_publisherFails_userAndTokenStillPersistedInDb() {
        log.info("▶ [FAULT ISOLATION] SpyPublisher.shouldFail=true → mô phỏng RabbitMQ / email broker down");
        log.info("  Thiết kế async: transaction COMMIT xong MỚI gọi publisher");
        log.info("  → dù publisher fail, DB đã committed → user + token an toàn");
        log.info("  So sánh: sync email TRONG transaction → fail = ROLLBACK → data mất");

        TestConfig.SHARED_SPY.setFailing(true);
        var cmd = new RegisterCommandDto("alice", "alice@test.com", "Secret@123");

        // execute() KHÔNG throw — trong Spring 6.1+, exception từ @TransactionalEventListener(AFTER_COMMIT)
        // được Spring isolate khỏi business transaction. Caller nhận kết quả thành công,
        // notification failure được log nhưng không propagate về caller.
        // → Fault isolation HOÀN TOÀN: business data an toàn, notification tách biệt.
        // So sánh: sync email TRONG transaction → email fail = ROLLBACK = exception propagates đến caller
        assertThatCode(() -> registerUseCase.execute(cmd))
                .as("execute() KHÔNG throw — Spring (6.1+) isolates AFTER_COMMIT listener failure. "
                        + "Nếu là sync email-in-transaction: email fail = ROLLBACK + exception về caller = data mất")
                .doesNotThrowAnyException();

        log.info("  → execute() hoàn thành KHÔNG exception (Spring isolates AFTER_COMMIT failure từ caller)");
        log.info("  → Kiểm tra DB state: transaction đã committed trước khi listener chạy...");

        long userCount  = userJpaRepository.count();
        long tokenCount = tokenJpaRepository.count();

        log.info("  → DB state: users={}, email_verification_tokens={}", userCount, tokenCount);

        assertThat(userCount)
                .as("User PHẢI tồn tại trong DB — transaction committed trước khi AFTER_COMMIT fire. "
                        + "Nếu là sync email-in-transaction, email fail = ROLLBACK = 0 users")
                .isEqualTo(1);

        assertThat(tokenCount)
                .as("EmailVerificationToken PHẢI tồn tại — lưu trong transaction (COMMIT), "
                        + "không bị rollback dù publisher fail ở AFTER_COMMIT phase")
                .isEqualTo(1);

        log.info("  ✔ Fault Isolation xác nhận: DB không bị ảnh hưởng bởi lỗi notification layer");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — No Ghost Message
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[No Ghost Message] publishEvent() queue xong rồi rollback → AFTER_COMMIT listener không được kích hoạt")
    void publishEvent_thenTransactionRollback_listenerNeverCalled() {
        log.info("▶ [NO GHOST MESSAGE] Gọi publishEvent() rồi force rollback");
        log.info("  Kỳ vọng: @TransactionalEventListener(AFTER_COMMIT) KHÔNG fire khi rollback");
        log.info("  → SpyPublisher.publish() = 0 lần");
        log.info("  So sánh sync: nếu email gửi TRƯỚC commit, email đi ra ngoài dù DB rollback → ghost message");

        assertThatThrownBy(() -> rollbackSimulator.publishThenRollback())
                .as("Method phải throw để trigger transaction rollback")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Forced rollback");

        log.info("  → Transaction đã rollback. Kiểm tra SpyPublisher...");
        log.info("  → SpyPublisher.publishCount() = {}", TestConfig.SHARED_SPY.publishCount());

        assertThat(TestConfig.SHARED_SPY.publishCount())
                .as("SpyPublisher.publish() KHÔNG được gọi khi transaction rollback. "
                        + "@TransactionalEventListener(AFTER_COMMIT) chỉ fire khi commit thành công. "
                        + "Nếu là sync email-in-transaction: email có thể gửi trước rollback "
                        + "→ user nhận email nhưng account không tồn tại (ghost message)")
                .isEqualTo(0);

        log.info("  ✔ No Ghost Message xác nhận: zero event published on rollback");
    }
}
