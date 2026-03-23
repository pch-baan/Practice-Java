package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserJpaRepository;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserProfileJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh fix đã hoạt động: BCrypt ngoài @Transactional → không còn pool exhaustion.
 *
 * ── Điều kiện test: GIỐNG HỆT CreateUserConnectionPoolExhaustionTest ────────
 *
 *   Pool size        = 10 connections  (production: 4 vCPU × 2 + 1 SSD)
 *   Connection timeout = 5,000ms
 *   BCrypt simulate  = 300ms sleep     (BCrypt trung bình trên 4 vCPU)
 *   Threads          = 165             (5 trên ngưỡng 160 của V1)
 *
 * ── Tại sao V2 không bị lỗi? ────────────────────────────────────────────────
 *
 * V1 (có bug):
 *   Hold time = DB query + BCrypt + DB write ≈ 330ms
 *   Max burst = 10 + floor(5000/330) × 10    = 160 request
 *   → 165 request > 160 → LỖI ✘
 *
 * V2 (đã fix):
 *   BCrypt chạy trước khi mở transaction → KHÔNG chiếm connection
 *   Hold time = chỉ DB query + DB write ≈ 30ms
 *   Max burst = 10 + floor(5000/30) × 10     = 1,670 request
 *   → 165 request << 1,670 → TẤT CẢ THÀNH CÔNG ✔
 *
 * ── Điều quan trọng cần chú ý ────────────────────────────────────────────────
 *
 * Sau khi BCrypt hoàn thành (~300ms), 165 threads cùng lúc tranh connection.
 * Nhưng vì mỗi thread chỉ giữ connection ~30ms:
 *
 *   Batch  1 (thread   1-10): t=300ms → t=330ms  (wait=0ms)
 *   Batch  2 (thread  11-20): t=330ms → t=360ms  (wait=30ms)
 *   ...
 *   Batch 17 (thread 161-165): t=780ms → t=810ms  (wait=480ms << 5000ms) ✔
 *
 * Thread cuối cùng chỉ chờ 480ms — rất xa so với timeout 5000ms.
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 * PostgreSQL đang chạy: docker-compose up postgres -d
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=5000"
})
class CreateUserV2ConnectionPoolFixTest {

    private static final Logger log = LoggerFactory.getLogger(CreateUserV2ConnectionPoolFixTest.class);

    // Cùng điều kiện BCrypt với CreateUserConnectionPoolExhaustionTest — đảm bảo so sánh công bằng
    @TestConfiguration
    static class SimulatedBCryptConfig {

        @Bean
        @Primary
        PasswordEncoder simulatedBCrypt() {
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    try {
                        Thread.sleep(300); // BCrypt trung bình ~300ms trên 4 vCPU
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "$2a$10$simulatedHash";
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return true;
                }
            };
        }
    }

    @Autowired private ICreateUserUseCase createUserUseCase; // inject CreateUserUseCaseImplV2
    @Autowired private IUserProfileJpaRepository userProfileJpaRepository;
    @Autowired private IUserJpaRepository userJpaRepository;

    @AfterEach
    void cleanup() {
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        log.info("[CLEANUP] Đã xóa toàn bộ dữ liệu test");
    }

    @Test
    @DisplayName("[Fix Verified] V2: BCrypt ngoài @Transactional → 165 request đồng thời đều thành công")
    void execute_withV2_165ConcurrentRequests_allShouldSucceed() throws InterruptedException {
        int threadCount = 165;

        log.info("▶ [FIX VERIFIED] Pool=10, timeout=5000ms, BCrypt=300ms, threads={}", threadCount);
        log.info("  → V1 (bug): hold=330ms → max burst=160 → lỗi tại request 161+");
        log.info("  → V2 (fix): hold=30ms  → max burst=1670 → 165 request đều an toàn");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        List<UserResponseDto> successes = Collections.synchronizedList(new ArrayList<>());
        List<Exception>       failures  = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    successes.add(createUserUseCase.execute(
                            new CreateUserCommandDto("user" + idx, "user" + idx + "@test.com", "Secret@123")));
                } catch (Exception e) {
                    failures.add(e);
                    log.warn("  [Thread-{}] ✘ [{}] {}", idx, e.getClass().getSimpleName(), e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        log.info("  → Phát lệnh 'Go!' cho cả {} thread...", threadCount);
        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);

        log.info("  → Tổng kết: successes={}, failures={}", successes.size(), failures.size());

        // ── Assert ────────────────────────────────────────────────────────────

        // V2: BCrypt ngoài transaction, hold time ~30ms
        // Thread cuối (165) chỉ chờ 16 × 30ms = 480ms << timeout 5000ms → không ai fail
        assertThat(failures)
                .as("V2: không request nào bị timeout — BCrypt không còn chiếm connection")
                .isEmpty();

        assertThat(successes)
                .as("V2: tất cả %d request phải thành công", threadCount)
                .hasSize(threadCount);
    }
}
