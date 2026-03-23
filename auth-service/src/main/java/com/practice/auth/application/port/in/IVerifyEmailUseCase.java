package com.practice.auth.application.port.in;

import com.practice.auth.application.dto.AuthTokenDto;

public interface IVerifyEmailUseCase {

    AuthTokenDto execute(String rawToken);
}
