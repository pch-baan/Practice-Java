package com.practice.auth.application.dto;

public record RegisterCommandDto(
        String username,
        String email,
        String password
) {}
