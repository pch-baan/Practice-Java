package com.practice.worker.infrastructure.messaging.consumer.auth;

import com.practice.worker.application.port.IWorkerEmailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class UserRegisteredNotificationConsumerIT {

    // ── Infrastructure ───────────────────────────────────────────────────────

    /**
     * Static: one container shared across all tests in this class.
     * @ServiceConnection auto-configures spring.rabbitmq.host/port from the container.
     */
    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:4-management");

    // ── Test dependencies ────────────────────────────────────────────────────

    /** Replace real email adapter with a mock — no SMTP calls during test. */
    @MockitoBean
    IWorkerEmailPort emailPort;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /** Reuse the same converter already configured for the listener. */
    @Autowired
    @Qualifier("defaultMessageConverter")
    Jackson2JsonMessageConverter converter;

    @BeforeEach
    void setUp() {
        // RabbitTemplate defaults to SimpleMessageConverter (byte[]/String).
        // We need Jackson so the consumer can deserialize to UserRegisteredMessage.
        rabbitTemplate.setMessageConverter(converter);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void shouldSendVerificationEmailWhenUserRegistered() {
        // GIVEN
        String token = UUID.randomUUID().toString();
        var message = new UserRegisteredMessage("user@test.com", token);

        // WHEN — publish to the real exchange/queue inside the container
        rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);

        // THEN — consumer processes async, await until emailPort is called
        await().atMost(5, SECONDS).untilAsserted(() ->
            verify(emailPort).sendVerificationEmail("user@test.com", token)
        );
    }

    @Test
    void shouldIgnoreDuplicateMessage_whenSameTokenPublishedTwice() throws InterruptedException {
        // GIVEN
        String token = UUID.randomUUID().toString();
        var message = new UserRegisteredMessage("user@test.com", token);

        // WHEN — first message: should be processed normally
        rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);
        await().atMost(5, SECONDS).untilAsserted(() ->
            verify(emailPort, atLeastOnce()).sendVerificationEmail(anyString(), eq(token))
        );

        // WHEN — duplicate (same rawToken): ProcessedMessageTracker must skip it
        rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);
        Thread.sleep(500); // give consumer time to finish consuming the duplicate

        // THEN — email was sent exactly once despite two messages
        verify(emailPort, times(1)).sendVerificationEmail(anyString(), eq(token));
    }
}
