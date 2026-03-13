package com.practice.auth.application.port.in;

public interface ILogoutUseCase {

    void execute(String rawRefreshToken);
}
