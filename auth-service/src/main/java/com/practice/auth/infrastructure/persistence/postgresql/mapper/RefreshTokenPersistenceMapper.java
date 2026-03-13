package com.practice.auth.infrastructure.persistence.postgresql.mapper;

import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.infrastructure.persistence.postgresql.entity.RefreshTokenJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefreshTokenPersistenceMapper {

    public RefreshTokenJpaEntity toJpaEntity(RefreshToken token) {
        return RefreshTokenJpaEntity.builder()
            .id(token.getId() != null ? token.getId() : UUID.randomUUID())
            .userId(token.getUserId())
            .tokenHash(token.getTokenHash())
            .expiresAt(token.getExpiresAt())
            .revoked(token.isRevoked())
            .createdAt(token.getCreatedAt())
            .build();
    }

    public RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstruct(
            entity.getId(),
            entity.getUserId(),
            entity.getTokenHash(),
            entity.getExpiresAt(),
            entity.isRevoked(),
            entity.getCreatedAt()
        );
    }
}
