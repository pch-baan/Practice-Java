package com.practice.worker.listeners.auth;

import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.out.IEmailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserRegisteredNotificationConsumer {

    private final IEmailPort emailPort;

    @RabbitListener(
            bindings = @QueueBinding(
                    value    = @Queue(value = "${worker.messaging.user-registered.queue}", durable = "true"),
                    exchange = @Exchange(value = "${worker.messaging.user-registered.exchange}", type = ExchangeTypes.TOPIC),
                    key      = "${worker.messaging.user-registered.routing-key}"
            ),
            containerFactory = "defaultListenerFactory"
    )
    public void handleUserRegistered(UserRegisteredEvent dto) {
        emailPort.sendVerificationEmail(dto.email(), dto.rawToken());
    }
}
