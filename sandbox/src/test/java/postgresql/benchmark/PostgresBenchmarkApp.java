package postgresql.benchmark;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot entry point cho các benchmark test PostgreSQL.
 * Giới hạn component scan trong package này — không can thiệp vào sandbox.
 */
@SpringBootApplication
public class PostgresBenchmarkApp {
}
