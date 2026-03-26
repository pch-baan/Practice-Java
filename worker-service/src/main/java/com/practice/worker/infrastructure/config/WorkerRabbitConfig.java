package com.practice.worker.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class WorkerRabbitConfig {

    // ── Message Converter ────────────────────────────────────────────────────

    @Bean("defaultMessageConverter")
    public Jackson2JsonMessageConverter defaultMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ── Exchanges ────────────────────────────────────────────────────────────

    /** Topic exchange published by auth-service. */
    @Bean("authTopicExchange")
    public TopicExchange authTopicExchange(
            @Value("${worker.consumer.user-registered.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    /**
     * Dead-Letter Exchange (DLX) — receives messages that exhausted all retries.
     * Using DirectExchange so each failed queue routes to its own DLQ via routing key.
     */
    @Bean("workerDlx")
    public DirectExchange workerDlx(
            @Value("${worker.consumer.dlx.exchange}") String dlxName) {
        return new DirectExchange(dlxName);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    /**
     * Main queue. x-dead-letter-exchange routes rejected/failed messages to DLX
     * instead of dropping them.
     */
    @Bean("userRegisteredQueue")
    public Queue userRegisteredQueue(
            @Value("${worker.consumer.user-registered.queue}") String queueName,
            @Value("${worker.consumer.dlx.exchange}") String dlxExchange,
            @Value("${worker.consumer.user-registered.dlq-routing-key}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    /** Dead-Letter Queue — holds messages that failed all retry attempts. */
    @Bean("userRegisteredDlq")
    public Queue userRegisteredDlq(
            @Value("${worker.consumer.user-registered.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding userRegisteredBinding(
            @Qualifier("userRegisteredQueue") Queue queue,
            @Qualifier("authTopicExchange") TopicExchange exchange,
            @Value("${worker.consumer.user-registered.routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    public Binding userRegisteredDlqBinding(
            @Qualifier("userRegisteredDlq") Queue dlq,
            @Qualifier("workerDlx") DirectExchange dlx,
            @Value("${worker.consumer.user-registered.dlq-routing-key}") String dlqRoutingKey) {
        return BindingBuilder.bind(dlq).to(dlx).with(dlqRoutingKey);
    }

    // ── Listener Factory ─────────────────────────────────────────────────────

    /**
     * Retry policy: 3 attempts, exponential backoff 1s → 2s → 4s (max 10s).
     * After all attempts fail: RejectAndDontRequeue → message routed to DLX/DLQ.
     * setDefaultRequeueRejected(false): prevents accidental re-queue on uncaught exception.
     */
    /**
     * Factory for I/O-bound queues (email, HTTP calls, etc.).
     * concurrentConsumers=3: 3 threads start immediately.
     * maxConcurrentConsumers=8: scale up to 8 under load.
     * I/O-bound workload: threads spend ~90% waiting → high concurrency is safe.
     */
    @Bean("defaultListenerFactory")
    public SimpleRabbitListenerContainerFactory defaultListenerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("defaultMessageConverter") Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1_000, 2.0, 10_000)   // initial=1s, multiplier=2, max=10s
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
