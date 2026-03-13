package com.practice.auth.application.port.in;

import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.RefreshTokenCommandDto;

public interface IRefreshTokenUseCase {

    AuthTokenDto execute(RefreshTokenCommandDto command);
}
