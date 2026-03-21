package com.practice.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point cho Integration Tests trong user-service module.
 * <p>
 * user-service là JAR library — không có @SpringBootApplication trong main source.
 * Class này nằm trong src/test/java để Spring Boot Test tìm được ApplicationContext.
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
