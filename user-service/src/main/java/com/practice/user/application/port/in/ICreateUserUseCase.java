package com.practice.user.application.port.in;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;

public interface ICreateUserUseCase {

    UserResponseDto execute(CreateUserCommandDto command);
}
