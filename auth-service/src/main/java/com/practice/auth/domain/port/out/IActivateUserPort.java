package com.practice.auth.domain.port.out;

import java.util.UUID;

public interface IActivateUserPort {

    void activate(UUID userId);
}
