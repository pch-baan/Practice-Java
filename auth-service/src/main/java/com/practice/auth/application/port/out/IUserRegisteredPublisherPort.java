package com.practice.auth.application.port.out;

import com.practice.auth.application.event.UserRegisteredEvent;

public interface IUserRegisteredPublisherPort {

    void publish(UserRegisteredEvent event);
}
