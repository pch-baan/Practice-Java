package com.practice.auth.domain.service;

import com.practice.auth.domain.exception.AuthDomainException;
import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.domain.model.UserCredential;

public class AuthDomainService {

    public void validateUserCanLogin(UserCredential credential) {
        if (!"ACTIVE".equals(credential.status())) {
            throw new AuthDomainException("User is not active");
        }
    }

    public void validateRefreshToken(RefreshToken token) {
        if (!token.isValid()) {
            throw new AuthDomainException("Refresh token is expired or revoked");
        }
    }
}
