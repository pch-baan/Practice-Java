package com.practice.worker.infrastructure.email;

import com.practice.worker.application.port.IWorkerEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback used when spring.mail.host is not configured (dev/test environment).
 */
@Component
public class WorkerNoOpEmailAdapter implements IWorkerEmailPort {

    private static final Logger log = LoggerFactory.getLogger(WorkerNoOpEmailAdapter.class);

    @Override
    public void sendVerificationEmail(String to, String rawToken) {
        log.warn("[DEV] Email not configured. Verification token for {}: {}", to, rawToken);
    }
}
