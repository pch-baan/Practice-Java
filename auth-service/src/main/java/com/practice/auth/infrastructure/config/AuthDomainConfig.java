package com.practice.auth.infrastructure.config;

import com.practice.auth.domain.service.AuthDomainService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthDomainConfig {

    @Bean
    public AuthDomainService authDomainService() {
        return new AuthDomainService();
    }
}
