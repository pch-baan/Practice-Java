package com.practice.auth.infrastructure.messaging.rabbitmq;

import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.out.IUserRegisteredPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitMQUserRegisteredPublisher implements IUserRegisteredPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Value("${auth.producer.user-registered.exchange}")
    private String exchange;

    @Value("${auth.producer.user-registered.routing-key}")
    private String routingKey;

    @Override
    public void publish(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
