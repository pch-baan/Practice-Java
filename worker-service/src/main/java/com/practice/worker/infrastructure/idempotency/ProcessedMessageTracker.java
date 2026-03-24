package com.practice.worker.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency guard for RabbitMQ consumers.
 *
 * Uses rawToken as the deduplication key — each registration produces exactly one
 * unique token, so it is a natural idempotency key.
 *
 * TTL (24h) matches the email verification token expiry, so entries are evicted
 * before a legitimate second attempt could ever occur.
 */
@Component
public class ProcessedMessageTracker {

    private static final Logger log = LoggerFactory.getLogger(ProcessedMessageTracker.class);

    /** Must match email verification token expiry in auth-service. */
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> processed = new ConcurrentHashMap<>();

    /**
     * Atomically marks the key as processed.
     *
     * @return true  — first time seen, caller should process the message
     *         false — duplicate, caller should skip
     */
    public boolean tryMarkAsProcessed(String key) {
        return processed.putIfAbsent(key, Instant.now().toEpochMilli()) == null;
    }

    /** Hourly cleanup so memory does not grow unbounded over a long uptime. */
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    public void evictExpired() {
        long cutoff = Instant.now().toEpochMilli() - TTL_MS;
        int before = processed.size();
        processed.entrySet().removeIf(e -> e.getValue() < cutoff);
        int removed = before - processed.size();
        if (removed > 0) {
            log.debug("Evicted {} expired idempotency keys", removed);
        }
    }
}
