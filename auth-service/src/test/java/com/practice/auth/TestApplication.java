package com.practice.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point cho Integration Tests trong auth-service module.
 * <p>
 * Cần khai báo tường minh vì:
 * 1. AuthServiceApplication tồn tại ở main source → conflict "multiple @SpringBootConfiguration"
 *    → fix bằng @SpringBootTest(classes = TestApplication.class)
 * <p>
 * 2. @SpringBootApplication.scanBasePackages ảnh hưởng ComponentScan nhưng KHÔNG tự động set
 *    AutoConfigurationPackages (dùng bởi @EnableJpaRepositories auto-config).
 *    → Phải khai báo tường minh @EnableJpaRepositories + @EntityScan (giống ApiPortalApplication).
 * <p>
 * 3. auth-service phụ thuộc beans từ user-service (ICreateUserUseCase, IGetUserCredentialUseCase,
 *    IActivateUserUseCase) nên cả 3 annotations đều phải bao gồm "com.practice.user".
 */
@SpringBootApplication(scanBasePackages = {
        "com.practice.auth",
        "com.practice.user"
})
@EnableJpaRepositories(basePackages = {
        "com.practice.auth.infrastructure",
        "com.practice.user.infrastructure"
})
@EntityScan(basePackages = {
        "com.practice.auth.infrastructure",
        "com.practice.user.infrastructure"
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
