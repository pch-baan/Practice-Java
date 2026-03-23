package com.practice.auth.domain.port.out;

import java.util.UUID;

public interface ICreateUserPort {

    CreatedUserResult createUser(String username, String email, String password);

    record CreatedUserResult(UUID userId, String email, String role) {}
}
