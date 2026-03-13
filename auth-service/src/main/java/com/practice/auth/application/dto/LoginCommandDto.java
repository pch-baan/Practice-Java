package com.practice.auth.application.dto;

public record LoginCommandDto(
    String username,
    String password
) {
}
