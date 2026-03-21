package annotation.Transactional.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserRegistrationServiceTest {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserProfileRepository profileRepo;

    @BeforeEach
    void cleanUp() {
        profileRepo.deleteAll();
        userRepo.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 1: Thành công — cả user và profile được commit cùng lúc
    // ─────────────────────────────────────────────────────────────────

    @Test
    void register_success_bothUserAndProfileSaved() {
        Long userId = registrationService.register("Hung", "hung@example.com", false);

        System.out.println("[TEST] Registered userId=" + userId);
        System.out.println("[TEST] Users in DB   : " + userRepo.count());
        System.out.println("[TEST] Profiles in DB: " + profileRepo.count());

        assertThat(userRepo.count()).isEqualTo(1);
        assertThat(profileRepo.count()).isEqualTo(1);

        // Profile phải trỏ đúng về userId vừa tạo
        UserProfile profile = profileRepo.findAll().getFirst();
        assertThat(profile.getUserId()).isEqualTo(userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 2: Fail — @Transactional rollback cả user lẫn profile
    // ─────────────────────────────────────────────────────────────────

    @Test
    void register_profileFail_rollbackBothUserAndProfile() {
        assertThatThrownBy(() ->
                registrationService.register("Nam", "nam@example.com", true)
        ).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Profile creation failed");

        System.out.println("[TEST] After rollback:");
        System.out.println("[TEST] Users in DB   : " + userRepo.count() + "  ← should be 0");
        System.out.println("[TEST] Profiles in DB: " + profileRepo.count() + "  ← should be 0");

        // @Transactional rollback → không có gì được lưu
        assertThat(userRepo.count()).isEqualTo(0);
        assertThat(profileRepo.count()).isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 3: Invariant — users == profiles sau mọi thao tác
    // ─────────────────────────────────────────────────────────────────

    @Test
    void register_mixSuccessAndFail_invariantAlwaysHolds() {
        // 3 success
        registrationService.register("Hung", "hung@example.com", false);
        registrationService.register("Nam",  "nam@example.com",  false);
        registrationService.register("Lan",  "lan@example.com",  false);

        // 2 fail
        try { registrationService.register("Fail1", "f1@example.com", true); } catch (Exception ignored) {}
        try { registrationService.register("Fail2", "f2@example.com", true); } catch (Exception ignored) {}

        long users    = userRepo.count();
        long profiles = profileRepo.count();

        System.out.println("[TEST] Users    : " + users    + "  ← should be 3");
        System.out.println("[TEST] Profiles : " + profiles + "  ← should be 3");
        System.out.println("[TEST] Invariant users == profiles: " + (users == profiles) + "  ← ✅");

        // Invariant: mỗi user đều có profile, không user "ma"
        assertThat(users).isEqualTo(3);
        assertThat(profiles).isEqualTo(3);
        assertThat(users).isEqualTo(profiles);
    }

    // ─────────────────────────────────────────────────────────────────
    // CASE 4: Rollback không ảnh hưởng đến các transaction khác
    // ─────────────────────────────────────────────────────────────────

    @Test
    void register_failDoesNotAffectPreviousSuccessfulRegistrations() {
        // Đăng ký thành công trước
        Long hungId = registrationService.register("Hung", "hung@example.com", false);

        // Đăng ký fail sau
        try { registrationService.register("Fail", "fail@example.com", true); } catch (Exception ignored) {}

        System.out.println("[TEST] Hung (id=" + hungId + ") vẫn tồn tại sau khi transaction khác rollback");
        System.out.println("[TEST] Users    : " + userRepo.count() + "  ← should be 1");
        System.out.println("[TEST] Profiles : " + profileRepo.count() + "  ← should be 1");

        // Hung vẫn còn nguyên, không bị ảnh hưởng
        assertThat(userRepo.findById(hungId)).isPresent();
        assertThat(userRepo.count()).isEqualTo(1);
        assertThat(profileRepo.count()).isEqualTo(1);
    }
}
