package com.practice.auth.application.port.out;

import java.util.UUID;

public interface IJwtPort {

    String generateAccessToken(UUID userId, String role);

    long getExpirationMs();
}
