package com.practice.auth.infrastructure.email;

import com.practice.auth.application.port.out.IEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback used when spring.mail.host is not configured (dev/test environment).
 * Prints verification link to log instead of sending a real email.
 */
@Component
public class NoOpEmailAdapter implements IEmailPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailAdapter.class);

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        log.warn("[DEV] Email not configured. Verification token for {}: {}", to, rawToken);
    }
}
