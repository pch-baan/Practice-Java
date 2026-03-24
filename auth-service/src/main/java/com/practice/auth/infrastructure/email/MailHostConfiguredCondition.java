package com.practice.auth.infrastructure.email;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that is true only when spring.mail.host is set to a non-blank value.
 * {@code @ConditionalOnProperty(name = "spring.mail.host")} is insufficient because
 * an empty string default (e.g. {@code host: ${MAIL_HOST:}}) evaluates to true.
 */
public class MailHostConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty("spring.mail.host");
        return host != null && !host.isBlank();
    }
}
