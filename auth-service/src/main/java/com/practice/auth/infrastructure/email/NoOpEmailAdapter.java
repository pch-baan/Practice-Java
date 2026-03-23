package com.practice.auth.infrastructure.email;

import com.practice.auth.application.port.out.IEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback dùng khi spring.mail.host chưa được cấu hình (môi trường dev/test).
 * In verification link ra log thay vì gửi email thật.
 */
@Component
public class NoOpEmailAdapter implements IEmailPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailAdapter.class);

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        log.warn("[DEV] Email not configured. Verification token for {}: {}", to, rawToken);
    }
}
