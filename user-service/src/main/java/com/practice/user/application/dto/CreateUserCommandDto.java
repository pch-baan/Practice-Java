package com.practice.user.application.dto;

public record CreateUserCommandDto(
        String username,
        String email,
        String password
) {}
