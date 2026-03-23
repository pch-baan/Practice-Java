package com.practice.auth.infrastructure.messaging.internal;

import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.out.IEmailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class UserRegisteredEventListener {

    private final IEmailPort emailPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        emailPort.sendVerificationEmail(event.email(), event.rawToken());
    }
}
