package com.practice.auth.application.usecase;

import com.practice.auth.application.port.in.ILogoutUseCase;
import com.practice.auth.domain.port.out.IRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.practice.auth.application.util.HashUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class LogoutUseCaseImpl implements ILogoutUseCase {

    private final IRefreshTokenRepository refreshTokenRepository;

    @Override
    public void execute(String rawRefreshToken) {
        String tokenHash = HashUtils.sha256(rawRefreshToken);
        refreshTokenRepository.revokeByTokenHash(tokenHash);
    }

}
