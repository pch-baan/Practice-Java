package com.practice.worker.infrastructure.messaging.consumer.auth;

/**
 * Worker-side representation of the user.registered RabbitMQ message.
 * Field names must match the JSON published by auth-service's UserRegisteredEvent.
 */
public record UserRegisteredMessage(String email, String rawToken) {
}
