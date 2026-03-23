package com.practice.auth.infrastructure.external;

import com.practice.auth.application.port.out.ICreateUserPort;
import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class CreateUserServiceAdapter implements ICreateUserPort {

    private final ICreateUserUseCase createUserUseCase;

    @Override
    public CreatedUserResult createUser(String username, String email, String password) {
        var dto = createUserUseCase.execute(new CreateUserCommandDto(username, email, password));
        return new CreatedUserResult(dto.id(), dto.email(), dto.role().name());
    }
}
