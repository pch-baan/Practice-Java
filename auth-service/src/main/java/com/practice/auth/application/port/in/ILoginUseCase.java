package com.practice.auth.application.port.in;

import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.LoginCommandDto;

public interface ILoginUseCase {

    AuthTokenDto execute(LoginCommandDto command);
}
