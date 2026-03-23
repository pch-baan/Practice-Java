package com.practice.auth.application.port.out;

import java.util.UUID;

public interface ICreateUserPort {

    CreatedUserResult createUser(String username, String email, String password);

    record CreatedUserResult(UUID userId, String email, String role) {}
}
