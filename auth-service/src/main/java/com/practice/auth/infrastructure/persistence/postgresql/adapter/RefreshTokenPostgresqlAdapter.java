package com.practice.auth.infrastructure.persistence.postgresql.adapter;

import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.domain.port.out.IRefreshTokenRepository;
import com.practice.auth.infrastructure.persistence.postgresql.mapper.RefreshTokenPersistenceMapper;
import com.practice.auth.infrastructure.persistence.postgresql.repository.IRefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenPostgresqlAdapter implements IRefreshTokenRepository {

    private final IRefreshTokenJpaRepository jpaRepository;
    private final RefreshTokenPersistenceMapper mapper;

    @Override
    public RefreshToken save(RefreshToken token) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(token)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        jpaRepository.revokeByTokenHash(tokenHash);
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        jpaRepository.revokeAllByUserId(userId);
    }
}
