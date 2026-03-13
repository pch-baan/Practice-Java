package com.practice.api.portal.user.controller;

import com.practice.api.portal.user.mapper.UserApiMapper;
import com.practice.api.portal.user.request.CreateUserRequest;
import com.practice.api.portal.user.response.UserResponse;
import com.practice.user.application.port.in.ICreateUserUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ICreateUserUseCase createUserUseCase;
    private final UserApiMapper userApiMapper;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        // Bước 1: HTTP request → Command (API layer không biết application DTO)
        var command = userApiMapper.toCommand(request);

        // Bước 2: Thực thi use case, nhận về application DTO
        var appDto = createUserUseCase.execute(command);

        // Bước 3: Application DTO → HTTP response (tách biệt contract HTTP khỏi application layer)
        var response = userApiMapper.toResponse(appDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
