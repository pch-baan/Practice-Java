package com.practice.worker.infrastructure.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerRabbitConfig {

    @Bean("defaultMessageConverter")
    public Jackson2JsonMessageConverter defaultMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("defaultListenerFactory")
    public SimpleRabbitListenerContainerFactory defaultListenerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("defaultMessageConverter") Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
