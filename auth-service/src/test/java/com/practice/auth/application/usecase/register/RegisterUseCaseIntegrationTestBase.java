package com.practice.auth.application.usecase.register;

import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.in.IRegisterUseCase;
import com.practice.auth.application.port.out.ICreateUserPort;
import com.practice.auth.application.port.out.IUserRegisteredPublisherPort;
import com.practice.auth.infrastructure.messaging.rabbitmq.RabbitMQUserRegisteredPublisher;
import com.practice.auth.infrastructure.persistence.postgresql.repository.IEmailVerificationTokenJpaRepository;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserJpaRepository;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserProfileJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared test infrastructure cho integration tests của RegisterUseCaseImpl.
 *
 * ── Thiết kế ──────────────────────────────────────────────────────────────────
 * Abstract base class chứa:
 *   • @SpringBootTest / @TestPropertySource — cấu hình chung
 *   • @TestConfiguration (TestConfig) — override beans cho toàn bộ test suite
 *   • Simulators (SpyPublisher, SyncEmailInTransactionSimulator, RollbackAfterPublishSimulator)
 *   • @AfterEach cleanup, runConcurrentRegister helper
 *
 * Spring cache application context theo configuration key → 2 subclass dùng CÙNG
 * một context, không tốn startup time thêm.
 *
 * ── Subclasses ────────────────────────────────────────────────────────────────
 *   • RegisterUseCaseAsyncVsSyncIntegrationTest  — correctness (Fault Isolation, No Ghost Message)
 *   • RegisterUseCaseThroughputIntegrationTest   — performance (Throughput benchmark)
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 * PostgreSQL đang chạy:  docker-compose up postgres -d
 * RabbitMQ đang chạy:    docker-compose up rabbitmq -d
 *
 * SpyPublisher là DECORATOR — wrap RabbitMQUserRegisteredPublisher thật:
 *   • Normal mode: ghi lại event + forward đến RabbitMQ thật → message thật được gửi
 *   • Fail mode:   throw RuntimeException trước khi forward → kiểm tra fault isolation
 * → Tests không chỉ kiểm tra cơ chế @TransactionalEventListener mà còn chứng minh
 *   real RabbitMQ integration hoạt động đúng trong cùng một lần chạy.
 *
 * Exchange isolation: auth.producer.user-registered.exchange=auth.exchange.test
 *   → Messages đến exchange test-only, không bind queue nào → tự drop
 *   → worker-service không consume test messages → không gửi email thật đến địa chỉ fake
 *
 * ── Tại sao PasswordEncoder được override? ───────────────────────────────────
 * BCrypt thật mất 100-500ms tuỳ phần cứng → timing không deterministic.
 * NoOpPasswordEncoder (~0ms) tách biệt biến kiểm soát: chỉ SMTP delay mới là
 * nguyên nhân thể hiện sự khác biệt throughput giữa 2 thiết kế.
 */
@SpringBootTest(classes = {com.practice.auth.TestApplication.class, RegisterUseCaseIntegrationTestBase.TestConfig.class})
@ActiveProfiles("local")
@TestPropertySource(properties = {
        // Exchange riêng cho test — tránh pollute production queue.
        // Messages đến auth.exchange.test bị drop (không có queue bind) → không ảnh hưởng worker-service.
        "auth.producer.user-registered.exchange=auth.exchange.test",
        // Pool size khớp với cấu hình thực tế (HikariCP default = 10)
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=10000"
})
abstract class RegisterUseCaseIntegrationTestBase {

    /** Logger tự động dùng tên của concrete subclass nhờ getClass(). */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ─────────────────────────────────────────────────────────────────────────
    // @TestConfiguration — override beans + cung cấp simulators
    // ─────────────────────────────────────────────────────────────────────────

    @TestConfiguration
    static class TestConfig {

        /** Spy shared toàn bộ test suite — reset() trong @AfterEach. */
        static final SpyPublisher SHARED_SPY = new SpyPublisher();

        /**
         * Decorator: wrap RabbitMQUserRegisteredPublisher thật.
         * Normal mode → ghi lại event + forward đến RabbitMQ thật.
         * Fail mode   → throw trước khi forward (kiểm tra fault isolation).
         *
         * Inject RabbitMQUserRegisteredPublisher thay vì IUserRegisteredPublisherPort
         * để tránh circular dependency (SpyPublisher cũng implements cùng interface).
         */
        @Bean
        @Primary
        IUserRegisteredPublisherPort spyPublisher(RabbitMQUserRegisteredPublisher realPublisher) {
            SHARED_SPY.setDelegate(realPublisher);
            return SHARED_SPY;
        }

        /**
         * BCrypt thật (~300ms) làm nhiễu timing → dùng NoOp (~0ms) để
         * SMTP_SIMULATION_MS là biến kiểm soát duy nhất trong throughput test.
         */
        @Bean
        @Primary
        PasswordEncoder noOpPasswordEncoder() {
            return new PasswordEncoder() {
                @Override public String encode(CharSequence raw) { return raw.toString(); }
                @Override public boolean matches(CharSequence raw, String encoded) { return true; }
            };
        }

        /**
         * Simulator: mô phỏng "email gửi đồng bộ TRONG transaction" (cách thiết kế sai).
         * Giữ DB connection thêm SMTP_SIMULATION_MS mỗi request — tắc nghẽn pool khi concurrent.
         * Dùng TransactionTemplate thay @Transactional để tránh CGLIB proxy trên inner static class.
         */
        @Bean
        SyncEmailInTransactionSimulator syncEmailSimulator(
                ICreateUserPort createUserPort,
                PlatformTransactionManager transactionManager) {
            return new SyncEmailInTransactionSimulator(createUserPort, transactionManager);
        }

        /**
         * Simulator: gọi publishEvent() rồi bắt buộc transaction rollback.
         * Dùng để chứng minh AFTER_COMMIT listener KHÔNG bao giờ fire khi rollback.
         * Dùng TransactionTemplate thay @Transactional để tránh CGLIB proxy trên inner static class.
         */
        @Bean
        RollbackAfterPublishSimulator rollbackSimulator(
                ApplicationEventPublisher eventPublisher,
                PlatformTransactionManager transactionManager) {
            return new RollbackAfterPublishSimulator(eventPublisher, transactionManager);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SpyPublisher — ghi lại events, hỗ trợ chế độ FAILING để test fault isolation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Decorator wrapping RabbitMQUserRegisteredPublisher thật.
     *
     *   Normal mode: record event → forward đến real publisher → message đến RabbitMQ thật
     *   Fail mode:   throw RuntimeException trước khi forward → kiểm tra fault isolation
     *
     * Delegate được inject qua setDelegate() khi Spring khởi tạo bean (TestConfig.spyPublisher).
     */
    static class SpyPublisher implements IUserRegisteredPublisherPort {

        private static final Logger log = LoggerFactory.getLogger(SpyPublisher.class);

        private final List<UserRegisteredEvent> captured =
                Collections.synchronizedList(new ArrayList<>());
        private volatile boolean shouldFail = false;
        private IUserRegisteredPublisherPort delegate;

        void setDelegate(IUserRegisteredPublisherPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(UserRegisteredEvent event) {
            if (shouldFail) {
                log.info("  [SpyPublisher] ✘ FAIL mode — throwing RuntimeException (mô phỏng publisher down)");
                throw new RuntimeException("[SpyPublisher] Simulated RabbitMQ / email broker failure");
            }
            captured.add(event);
            log.info("  [SpyPublisher] ✔ Event captured: email={} — forwarding to real RabbitMQ", event.email());
            delegate.publish(event); // ← forward đến RabbitMQ thật
        }

        void   reset()               { captured.clear(); shouldFail = false; }
        void   setFailing(boolean v) { shouldFail = v; }
        int    publishCount()        { return captured.size(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SyncEmailInTransactionSimulator
    // Tương đương RegisterUseCaseImpl nhưng "gửi email" TRONG transaction.
    // Thread.sleep(SMTP_MS) bên trong transaction = giữ DB connection suốt thời gian đó.
    // ─────────────────────────────────────────────────────────────────────────

    static class SyncEmailInTransactionSimulator {

        static final long SMTP_SIMULATION_MS = 1000; // ~1s: realistic average SMTP round-trip

        private final ICreateUserPort createUserPort;
        private final TransactionTemplate transactionTemplate;

        SyncEmailInTransactionSimulator(ICreateUserPort cp, PlatformTransactionManager txManager) {
            this.createUserPort      = cp;
            this.transactionTemplate = new TransactionTemplate(txManager);
        }

        /**
         * Đăng ký user với "email đồng bộ trong transaction".
         * DB connection bị giữ thêm SMTP_SIMULATION_MS — mô phỏng thiết kế sai.
         */
        void register(String username, String email) {
            transactionTemplate.executeWithoutResult(status -> {
                createUserPort.createUser(username, email, "Secret@123");
                try {
                    Thread.sleep(SMTP_SIMULATION_MS); // ← SMTP blocking TRONG transaction
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RollbackAfterPublishSimulator
    // publishEvent() đã queue event → transaction rollback → AFTER_COMMIT không fire.
    // ─────────────────────────────────────────────────────────────────────────

    static class RollbackAfterPublishSimulator {

        private final ApplicationEventPublisher eventPublisher;
        private final TransactionTemplate transactionTemplate;

        RollbackAfterPublishSimulator(ApplicationEventPublisher ep, PlatformTransactionManager txManager) {
            this.eventPublisher      = ep;
            this.transactionTemplate = new TransactionTemplate(txManager);
        }

        /**
         * Queue event bên trong transaction rồi mark rollback → AFTER_COMMIT KHÔNG fire.
         * Throw RuntimeException SAU khi template hoàn tất để test có thể assert.
         */
        void publishThenRollback() {
            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new UserRegisteredEvent("ghost@test.com", "fake-token"));
                status.setRollbackOnly(); // ← force rollback, AFTER_COMMIT sẽ không fire
            });
            // Throw NGOÀI template để test assertThatThrownBy vẫn bắt được
            throw new RuntimeException("Forced rollback after publishEvent() — no ghost message expected");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Beans & cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Autowired protected IRegisterUseCase                      registerUseCase;
    @Autowired protected SyncEmailInTransactionSimulator       syncEmailSimulator;
    @Autowired protected RollbackAfterPublishSimulator         rollbackSimulator;
    @Autowired protected IEmailVerificationTokenJpaRepository  tokenJpaRepository;
    @Autowired protected IUserProfileJpaRepository             userProfileJpaRepository;
    @Autowired protected IUserJpaRepository                    userJpaRepository;

    @AfterEach
    void cleanup() {
        // FK order: email_verification_tokens → user_profiles → users
        tokenJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        TestConfig.SHARED_SPY.reset();
        log.info("[CLEANUP] DB cleared + SpyPublisher reset");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — chạy N thread đồng thời, trả về wall-clock time (ms)
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    protected interface CheckedAction {
        void run(int index) throws Exception;
    }

    protected long runConcurrentRegister(int threads, String label, CheckedAction action)
            throws InterruptedException {

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threads);
        List<Exception> failures  = Collections.synchronizedList(new ArrayList<>());
        AtomicLong wallTime       = new AtomicLong();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // barrier — tất cả thread xuất phát CÙNG LÚC
                    long t0 = System.currentTimeMillis();
                    action.run(idx);
                    log.info("  [{}][Thread-{}] ✔ done in {}ms",
                            label, idx, System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    failures.add(e);
                    log.warn("  [{}][Thread-{}] ✘ {}: {}",
                            label, idx, e.getClass().getSimpleName(), e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }, label + "-t" + idx).start();
        }

        long wallStart = System.currentTimeMillis();
        startLatch.countDown(); // "Go!" — phát lệnh cho tất cả thread
        boolean done = doneLatch.await(30, TimeUnit.SECONDS);
        wallTime.set(System.currentTimeMillis() - wallStart);

        assertThat(done)
                .as("[%s] Tất cả %d thread phải hoàn thành trong 30s", label, threads)
                .isTrue();

        assertThat(failures)
                .as("[%s] Không được có exception nào trong %d concurrent requests. "
                        + "Failures: %s", label, threads, failures)
                .isEmpty();

        return wallTime.get();
    }
}
