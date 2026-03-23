package com.practice.auth.application.port.out;

import java.util.UUID;

public interface IActivateUserPort {

    void activate(UUID userId);
}
