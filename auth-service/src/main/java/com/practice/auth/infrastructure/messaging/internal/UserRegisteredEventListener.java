package com.practice.auth.infrastructure.messaging.internal;

import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.out.IUserRegisteredPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final IUserRegisteredPublisherPort publisherPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserRegisteredEvent event) {
        publisherPort.publish(event);
    }
}
