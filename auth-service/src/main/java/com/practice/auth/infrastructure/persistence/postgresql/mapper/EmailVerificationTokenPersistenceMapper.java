package com.practice.auth.infrastructure.persistence.postgresql.mapper;

import com.practice.auth.domain.model.EmailVerificationToken;
import com.practice.auth.infrastructure.persistence.postgresql.entity.EmailVerificationTokenJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailVerificationTokenPersistenceMapper {

    public EmailVerificationTokenJpaEntity toJpaEntity(EmailVerificationToken token) {
        return EmailVerificationTokenJpaEntity.builder()
                .id(token.getId() != null ? token.getId() : UUID.randomUUID())
                .userId(token.getUserId())
                .tokenHash(token.getTokenHash())
                .expiresAt(token.getExpiresAt())
                .used(token.isUsed())
                .createdAt(token.getCreatedAt())
                .build();
    }

    public EmailVerificationToken toDomain(EmailVerificationTokenJpaEntity entity) {
        return EmailVerificationToken.reconstruct(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.isUsed(),
                entity.getCreatedAt()
        );
    }
}
