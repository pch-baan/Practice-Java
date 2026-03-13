package com.practice.user.infrastructure.config;

import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.service.UserDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserDomainConfig {

    @Bean
    public UserDomainService userDomainService(IUserRepository userRepository) {
        return new UserDomainService(userRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
