package com.practice.auth.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean("authExchange")
    public TopicExchange authExchange(
            @Value("${auth.messaging.user-registered.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean("authMessageConverter")
    public Jackson2JsonMessageConverter authMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            @Qualifier("authMessageConverter") Jackson2JsonMessageConverter authMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(authMessageConverter);
        return template;
    }
}
