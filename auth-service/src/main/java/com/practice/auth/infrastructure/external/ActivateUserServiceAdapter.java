package com.practice.auth.infrastructure.external;

import com.practice.auth.domain.port.out.IActivateUserPort;
import com.practice.user.application.port.in.IActivateUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ActivateUserServiceAdapter implements IActivateUserPort {

    private final IActivateUserUseCase activateUserUseCase;

    @Override
    public void activate(UUID userId) {
        activateUserUseCase.execute(userId);
    }
}
