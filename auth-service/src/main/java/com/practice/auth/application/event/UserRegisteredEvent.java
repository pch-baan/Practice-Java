package com.practice.auth.application.event;

public record UserRegisteredEvent(String email, String rawToken) {}
