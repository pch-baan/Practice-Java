package com.practice.user.application.port.in;

import java.util.UUID;

public interface IActivateUserUseCase {

    void execute(UUID userId);
}
