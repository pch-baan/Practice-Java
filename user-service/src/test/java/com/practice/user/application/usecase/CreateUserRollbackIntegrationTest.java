package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.model.UserProfile;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test cho Transaction Rollback trong CreateUserUseCaseImpl.
 *
 * ── Tại sao phải dùng @MockBean thay vì bean thật? ───────────────────────────
 *
 * Mục tiêu của test này là kiểm tra: nếu userProfileRepository.save() FAIL,
 * thì Spring có rollback toàn bộ transaction (bao gồm cả user vừa save) không?
 *
 * Vấn đề: bean thật (UserProfilePostgresqlAdapter) KHÔNG CÓ LÝ DO GÌ ĐỂ FAIL
 * trong điều kiện bình thường:
 *   - UserProfile.createEmpty() luôn tạo UUID mới → không bao giờ duplicate
 *   - DB đang chạy, schema đúng → INSERT luôn thành công
 *
 * Các cách "ép" fail mà không mock đều có vấn đề:
 *   - Tắt DB giữa chừng      → không kiểm soát được chính xác thời điểm
 *   - Drop table              → phá vỡ toàn bộ test suite
 *   - Gây constraint violation → không khả thi vì id luôn là UUID ngẫu nhiên
 *
 * @MockBean giải quyết vấn đề này bằng cách thay thế bean thật bằng Mockito mock,
 * cho phép kiểm soát 100% hành vi: "tao muốn save() throw exception TẠI ĐÂY".
 *
 * ── Tại sao KHÔNG dùng @Transactional ở class level? ────────────────────────
 *
 * Nếu test method có @Transactional, nó sẽ bao bọc execute() trong cùng 1 transaction.
 * execute() dùng Propagation.REQUIRED → JOIN vào transaction của test, không tạo mới.
 *
 * Kết quả: khi exception xảy ra, Spring chỉ mark transaction là rollback-only,
 * nhưng transaction chưa thực sự được commit hay rollback cho đến khi test kết thúc.
 * Lúc đó mình KHÔNG THỂ query DB để kiểm tra rollback đã xảy ra hay chưa.
 *
 * Bỏ @Transactional ở test → execute() tự mở và tự đóng transaction của nó.
 * Khi exception xảy ra, Spring rollback ngay lập tức → sau đó mình query DB
 * để kiểm tra user có bị rollback không. ✔
 *
 * ── Tại sao Spring tạo ApplicationContext riêng cho class này? ───────────────
 *
 * Spring Boot Test cache và tái sử dụng ApplicationContext giữa các test class
 * để tiết kiệm thời gian khởi động (~5s mỗi lần).
 *
 * @MockBean thay đổi cấu trúc context: IUserProfileRepository không còn là bean
 * thật (UserProfilePostgresqlAdapter) mà là Mockito mock. Context này KHÁC với
 * context của CreateUserUseCaseImplIntegrationTest → Spring buộc phải tạo mới.
 *
 *   Context A (CreateUserUseCaseImplIntegrationTest):
 *     IUserProfileRepository → UserProfilePostgresqlAdapter (bean thật)
 *
 *   Context B (CreateUserRollbackIntegrationTest):
 *     IUserProfileRepository → MockitoMock (bean giả)
 *
 * Đây là lý do class này được tách riêng — không thể gộp chung vào class kia.
 *
 * ── Yêu cầu ──────────────────────────────────────────────────────────────────
 * PostgreSQL đang chạy: docker-compose up postgres -d
 */
@SpringBootTest
@ActiveProfiles("local")
class CreateUserRollbackIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CreateUserRollbackIntegrationTest.class);

    @Autowired  private ICreateUserUseCase createUserUseCase;
    @Autowired  private IUserRepository userRepository;
    @MockBean   private IUserProfileRepository userProfileRepository;

    @BeforeEach
    void setupProfileSaveToFail() {
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenThrow(new RuntimeException("Simulated DB failure: profile save failed"));

        log.info("[SETUP] IUserProfileRepository.save() → configured to throw RuntimeException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 6 — Transaction Rollback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Transaction] Profile save thất bại → toàn bộ transaction rollback, user không được lưu vào DB")
    void execute_whenProfileSaveFails_shouldRollbackUserPersistence() {
        log.info("▶ [ROLLBACK] Profile save ném exception → toàn bộ transaction bị rollback");
        log.info("  → Luồng: userRepository.save(user) ✔ → userProfileRepository.save() ✘ → ROLLBACK");

        var command = new CreateUserCommandDto("rollback_user", "rollback@test.com", "Secret@123");

        // Act — execute() sẽ:
        //   1. lưu user thành công (trong transaction)
        //   2. gọi userProfileRepository.save() → ném RuntimeException
        //   3. Spring catch exception → rollback transaction
        //   → user KHÔNG được commit vào DB
        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("profile save failed")
                .satisfies(ex -> log.info("  → Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));

        // Assert — user phải KHÔNG tồn tại trong DB (đã bị rollback)
        boolean userExists = userRepository.existsByEmail(EmailVO.of("rollback@test.com"));
        log.info("  ✔ existsByEmail('rollback@test.com') sau rollback = {}", userExists);

        assertThat(userExists)
                .as("User phải bị rollback khi profile save thất bại")
                .isFalse();
    }
}
