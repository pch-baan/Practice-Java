package com.practice.worker.listeners.auth;

import com.practice.worker.application.port.IWorkerEmailPort;
import com.practice.worker.infrastructure.idempotency.ProcessedMessageTracker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserRegisteredNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredNotificationConsumer.class);

    private final IWorkerEmailPort emailPort;
    private final ProcessedMessageTracker tracker;

    // Queue is declared programmatically in WorkerRabbitConfig (with DLQ args).
    // Retry + dead-lettering handled by defaultListenerFactory.
    @RabbitListener(queues = "${worker.consumer.user-registered.queue}", containerFactory = "defaultListenerFactory")
    public void handleUserRegistered(UserRegisteredMessage message) {
        if (!tracker.tryMarkAsProcessed(message.rawToken())) {
            log.warn("Duplicate message — email already sent to {}, skipping", message.email());
            return;
        }
        emailPort.sendVerificationEmail(message.email(), message.rawToken());
    }
}
