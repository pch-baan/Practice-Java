package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.exception.UserConflictException;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserJpaRepository;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserProfileJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test cho race condition trong CreateUserUseCaseImpl.
 *
 * ── Kịch bản cần test ────────────────────────────────────────────────────────
 *
 * 2 client gửi request tạo user với cùng 1 email vào đúng cùng 1 lúc.
 * Đây là vấn đề TOCTOU (Time-Of-Check-To-Use):
 *
 *   Thread-1: existsByEmail("x@test.com") → false  ──┐  (cả 2 pass check)
 *   Thread-2: existsByEmail("x@test.com") → false  ──┘  vì chưa ai save
 *
 *   Thread-1: userRepository.save(user1) → thành công  ✔
 *   Thread-2: userRepository.save(user2) → DB unique constraint nổ  ✘
 *                                          → DataIntegrityViolationException
 *                                          → UserConflictException
 *
 * Kết quả mong đợi: đúng 1 user được tạo, 1 request bị từ chối.
 *
 * ── Tại sao DB unique constraint là tuyến phòng thủ cuối? ───────────────────
 *
 * validateUniqueConstraints() trong UserDomainService kiểm tra trước khi save,
 * nhưng giữa 2 thao tác đó có một "cửa sổ" thời gian rất ngắn. Nếu 2 thread
 * đều qua được check đó cùng lúc, chỉ có DB unique constraint mới chặn được.
 *
 * UserPostgresqlAdapter đã xử lý điều này:
 *   catch (DataIntegrityViolationException) → throw UserConflictException(...)
 *
 * ── Tại sao dùng CountDownLatch? ─────────────────────────────────────────────
 *
 * Nếu start 2 thread bình thường, Thread-1 có thể chạy xong hoàn toàn trước
 * khi Thread-2 bắt đầu → không có race condition, test không có ý nghĩa.
 *
 * CountDownLatch giải quyết bằng cách:
 *   1. startLatch(1): cả 2 thread block tại await(), chờ lệnh "Go!"
 *   2. startLatch.countDown(): main thread phát lệnh → cả 2 thread xuất phát CÙNG LÚC
 *   3. doneLatch(2): main thread chờ cả 2 thread hoàn thành mới tiếp tục assert
 *
 * ── Tại sao KHÔNG dùng @Transactional? ──────────────────────────────────────
 *
 * Mỗi thread cần transaction độc lập để commit riêng biệt — đó mới là
 * điều kiện tạo ra race condition thực sự. Nếu test có @Transactional,
 * tất cả đều chạy trong 1 transaction → không simulate được concurrent commits.
 *
 * ── Cleanup ──────────────────────────────────────────────────────────────────
 *
 * Vì không có @Transactional, data được commit thật vào DB.
 * @AfterEach xóa profiles trước (FK constraint), rồi mới xóa users.
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 * PostgreSQL đang chạy: docker-compose up postgres -d
 */
@SpringBootTest
@ActiveProfiles("local")
class CreateUserConcurrentIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CreateUserConcurrentIntegrationTest.class);

    @Autowired private ICreateUserUseCase createUserUseCase;
    @Autowired private IUserRepository userRepository;
    @Autowired private IUserProfileJpaRepository userProfileJpaRepository;
    @Autowired private IUserJpaRepository userJpaRepository;

    @AfterEach
    void cleanup() {
        // Xóa profiles trước vì FK: user_profiles.user_id → users.id
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        log.info("[CLEANUP] Đã xóa toàn bộ dữ liệu test");
    }

    @Test
    @DisplayName("[Concurrent] 2 client tạo user cùng email cùng lúc → chỉ 1 user được tạo, 1 bị từ chối")
    void execute_concurrentSameEmail_onlyOneUserShouldBeCreated() throws InterruptedException {
        log.info("▶ [CONCURRENT] 2 thread cùng gọi execute() với email='concurrent@test.com'");

        // startLatch(1): giữ cả 2 thread lại, đợi lệnh "Go!" cùng lúc
        CountDownLatch startLatch = new CountDownLatch(1);
        // doneLatch(2): main thread chờ cả 2 thread hoàn thành mới tiến hành assert
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<UserResponseDto> successes = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        Runnable task1 = () -> {
            try {
                startLatch.await(); // block tại đây, chờ lệnh "Go!"
                log.info("  [Thread-1] → Bắt đầu: username=client1, email=concurrent@test.com");
                successes.add(createUserUseCase.execute(
                        new CreateUserCommandDto("client1", "concurrent@test.com", "Secret@123")));
                log.info("  [Thread-1] ✔ Tạo thành công");
            } catch (Exception e) {
                exceptions.add(e);
                log.info("  [Thread-1] ✘ Exception: [{}] {}", e.getClass().getSimpleName(), e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable task2 = () -> {
            try {
                startLatch.await(); // block tại đây, chờ lệnh "Go!"
                log.info("  [Thread-2] → Bắt đầu: username=client2, email=concurrent@test.com");
                successes.add(createUserUseCase.execute(
                        new CreateUserCommandDto("client2", "concurrent@test.com", "Secret@123")));
                log.info("  [Thread-2] ✔ Tạo thành công");
            } catch (Exception e) {
                exceptions.add(e);
                log.info("  [Thread-2] ✘ Exception: [{}] {}", e.getClass().getSimpleName(), e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(task1).start();
        new Thread(task2).start();

        log.info("  → Cả 2 thread đã sẵn sàng. Phát lệnh 'Go!'...");
        startLatch.countDown(); // "Go!" — cả 2 thread xuất phát cùng lúc

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).as("Cả 2 thread phải hoàn thành trong 10 giây").isTrue();

        log.info("  → Tổng kết: successes={}, exceptions={}", successes.size(), exceptions.size());
        exceptions.forEach(e ->
                log.info("  → Exception bị từ chối: [{}] {}", e.getClass().getSimpleName(), e.getMessage()));

        // Đúng 1 request thành công
        assertThat(successes)
                .as("Đúng 1 user phải được tạo thành công")
                .hasSize(1);

        // Đúng 1 request bị từ chối với UserConflictException
        assertThat(exceptions)
                .as("Đúng 1 request phải bị từ chối")
                .hasSize(1);
        assertThat(exceptions.get(0))
                .as("Exception phải là UserConflictException")
                .isInstanceOf(UserConflictException.class);

        // DB chỉ có đúng 1 user với email này
        assertThat(userRepository.existsByEmail(EmailVO.of("concurrent@test.com")))
                .as("Phải có đúng 1 user trong DB với email này")
                .isTrue();
    }
}
