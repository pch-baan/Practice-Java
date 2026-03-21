package annotation.Transactional.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class SandboxApp {

    private final UserRegistrationService registrationService;
    private final UserRepository userRepo;
    private final UserProfileRepository profileRepo;

    public static void main(String[] args) {
        SpringApplication.run(SandboxApp.class, args);
    }

    @Bean
    CommandLineRunner run() {
        return args -> {
            log.info("=== CASE 1: Success — cả user và profile được lưu ===");
            Long userId = registrationService.register("Hung", "hung@example.com", false);
            log.info("[PRODUCTION] Registered userId={}", userId);
            log.info("[PRODUCTION] Users in DB   : {}", userRepo.count());
            log.info("[PRODUCTION] Profiles in DB: {}", profileRepo.count());
            log.info("[PRODUCTION] Consistent?   : {}  ← ✅",
                    userRepo.count() == profileRepo.count());

            log.info("");
            log.info("=== CASE 2: Fail — @Transactional rollback cả user lẫn profile ===");
            try {
                registrationService.register("Nam", "nam@example.com", true);
            } catch (RuntimeException e) {
                log.info("[PRODUCTION] Exception caught: {}", e.getMessage());
            }
            log.info("[PRODUCTION] Users in DB   : {}", userRepo.count());
            log.info("[PRODUCTION] Profiles in DB: {}", profileRepo.count());
            log.info("[PRODUCTION] Consistent?   : {}  ← ✅",
                    userRepo.count() == profileRepo.count());

            // Invariant: users == profiles (mỗi user đều có profile)
            boolean invariant = userRepo.count() == profileRepo.count();
            log.info("");
            log.info("[PRODUCTION] Invariant users == profiles: {}  ← should be true", invariant);
        };
    }
}
