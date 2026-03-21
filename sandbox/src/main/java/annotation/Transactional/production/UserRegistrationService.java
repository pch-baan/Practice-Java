package annotation.Transactional.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepo;
    private final UserProfileRepository profileRepo;

    /**
     * Đăng ký user + tạo profile trong cùng 1 transaction.
     *
     * @Transactional đảm bảo all-or-nothing:
     *   - Nếu thành công → cả user lẫn profile được commit cùng lúc
     *   - Nếu exception → cả hai bị rollback, DB sạch như trước
     *
     * Khi forceProfileFail=true → giả lập lỗi sau khi save user,
     * Spring sẽ tự rollback user (dù userRepo.save() đã chạy).
     */
    @Transactional
    public Long register(String name, String email, boolean forceProfileFail) {
        User user = userRepo.save(User.builder()
                .name(name)
                .email(email)
                .build());
        // ↑ Hibernate đưa vào session, CHƯA commit vào DB

        log.info("[PRODUCTION] User pending : id={}, name={}", user.getId(), user.getName());

        if (forceProfileFail) {
            // Throw trước khi save profile — Spring sẽ rollback cả user
            throw new RuntimeException("Profile creation failed (simulated)!");
        }

        profileRepo.save(UserProfile.builder()
                .userId(user.getId())
                .bio("Hello, I'm " + name)
                .build());
        // ↑ Cũng chưa commit

        log.info("[PRODUCTION] Profile pending: userId={}", user.getId());

        return user.getId();
        // ↑ Method return → Spring commits transaction
        // Cả user lẫn profile được ghi vào DB cùng lúc
    }
}
