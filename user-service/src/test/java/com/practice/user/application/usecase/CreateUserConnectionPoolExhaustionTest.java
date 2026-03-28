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
 * Test chứng minh vấn đề: BCrypt nằm trong @Transactional gây connection pool exhaustion.
 *
 * ── Thông số thực tế (production server: 4 vCPU, 8GB RAM) ────────────────────
 *
 * Pool size (HikariCP):
 *   Công thức: (vCPU × 2) + disk = (4 × 2) + 1 = 9 → làm tròn 10
 *   maximum-pool-size = 10
 *
 * Connection timeout: 5,000ms (5 giây)
 *
 * Hold time mỗi request (BCrypt TRONG @Transactional):
 *   existsByEmail + existsByUsername + BCrypt + save(user) + save(profile)
 *   ≈ 5ms + 5ms + 300ms + 10ms + 10ms = 330ms
 *
 * ── Tính ngưỡng gây lỗi ──────────────────────────────────────────────────────
 *
 * Cứ 330ms, pool xử lý được 1 batch = 10 request.
 * Trong timeout 5000ms, số batch có thể xử lý:
 *   floor(5000 / 330) = 15 batches → 15 × 10 = 150 request
 *
 * Max burst an toàn = pool_size + 150 = 10 + 150 = 160 request
 *
 * → Kết luận: > 160 request đến CÙNG LÚC → bắt đầu có lỗi
 *
 * ── Cách test hoạt động ───────────────────────────────────────────────────────
 *
 * Gửi 165 request đồng thời (5 trên ngưỡng 160).
 *
 * Kịch bản với BCrypt TRONG @Transactional:
 *   Batch  1 (req   1-10):  lấy connection ngay, xử lý t=0ms → t=330ms
 *   Batch  2 (req  11-20):  chờ đến t=330ms → xử lý t=330ms → t=660ms
 *   ...
 *   Batch 16 (req 151-160): chờ đến t=4950ms → xử lý (wait=4950ms < 5000ms) ✔
 *   Batch 17 (req 161-165): chờ đến t=5280ms → wait=5280ms > 5000ms → TIMEOUT ✘
 *
 * Kết quả mong đợi: ~160 thành công, ~5 thất bại (lỗi infrastructure, không phải domain).
 *
 * ── Tại sao BCrypt simulate 300ms thay vì dùng BCrypt thật? ─────────────────
 *
 * BCrypt thật mất 100-500ms tuỳ phần cứng → test không deterministic.
 * 300ms là thời gian trung bình trên server 4 vCPU — đúng với thực tế cần chứng minh.
 * Sleep cố định 300ms đảm bảo test luôn reproduce được vấn đề trên mọi máy.
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 * PostgreSQL đang chạy: docker-compose up postgres -d
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        // Đúng với production: (4 vCPU × 2) + 1 SSD = 10 connections
        "spring.datasource.hikari.maximum-pool-size=10",
        // Đúng với production: timeout 5 giây
        "spring.datasource.hikari.connection-timeout=5000"
})
class CreateUserConnectionPoolExhaustionTest {

    private static final Logger log = LoggerFactory.getLogger(CreateUserConnectionPoolExhaustionTest.class);

    // ── Simulate BCrypt 300ms — đúng với thực tế trên server 4 vCPU ─────────
    @TestConfiguration
    static class SimulatedBCryptConfig {

        @Bean
        @Primary // override BCryptPasswordEncoder trong UserDomainConfig
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

    @Autowired private ICreateUserUseCase createUserUseCase;
    @Autowired private IUserProfileJpaRepository userProfileJpaRepository;
    @Autowired private IUserJpaRepository userJpaRepository;

    @AfterEach
    void cleanup() {
        // Xóa profiles trước (FK: user_profiles.user_id → users.id), rồi mới xóa users
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        log.info("[CLEANUP] Đã xóa toàn bộ dữ liệu test");
    }

    @Test
    @DisplayName("[High Load - Pool=10, Timeout=5s, BCrypt=300ms] 165 request đồng thời → không pool exhaustion (V3: BCrypt ngoài transaction)")
    void execute_moreThan160ConcurrentRequests_exhaustsConnectionPool() throws InterruptedException {
        // Ngưỡng tính toán: 160 request an toàn, 161+ bắt đầu lỗi
        // Gửi 165 request (5 trên ngưỡng) để kết quả rõ ràng
        int threadCount = 165;

        log.info("▶ [POOL EXHAUSTION] Pool=10, timeout=5000ms, BCrypt=300ms, threads={}", threadCount);
        log.info("  → Tính toán: max burst an toàn = 10 + floor(5000/330)*10 = 160 request");
        log.info("  → Dự đoán: ~160 thành công, ~5 thất bại do connection timeout");

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
                    log.info("  [Thread-{}] ✘ [{}]", idx, e.getClass().getSimpleName());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        log.info("  → Phát lệnh 'Go!' cho cả {} thread...", threadCount);
        startLatch.countDown();
        // Chờ tối đa 60s: 16 batches × 330ms + buffer = ~10s thực tế
        doneLatch.await(60, TimeUnit.SECONDS);

        log.info("  → Tổng kết: successes={}, failures={}", successes.size(), failures.size());
        failures.stream()
                .map(e -> e.getClass().getSimpleName())
                .distinct()
                .forEach(type -> log.info("  → Failure type: [{}]", type));

        // ── Assert ────────────────────────────────────────────────────────────

        // V3: BCrypt chạy NGOÀI transaction → hold time chỉ ~20ms (2 INSERT)
        // Max burst = 10 + floor(5000/20) × 10 = 2,510 request >> 165
        // → Tất cả request phải thành công, không ai bị timeout
        assertThat(failures)
                .as("V3: BCrypt ngoài transaction → không pool exhaustion, failures phải rỗng")
                .isEmpty();

        assertThat(successes)
                .as("V3: tất cả %d request phải thành công", threadCount)
                .hasSize(threadCount);
    }
}
