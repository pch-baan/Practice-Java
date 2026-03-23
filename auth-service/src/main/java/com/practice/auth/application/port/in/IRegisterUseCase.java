package com.practice.auth.application.port.in;

import com.practice.auth.application.dto.RegisterCommandDto;

public interface IRegisterUseCase {

    void execute(RegisterCommandDto command);
}
