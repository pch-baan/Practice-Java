package com.practice.auth.infrastructure.persistence.postgresql.adapter;

import com.practice.auth.domain.model.EmailVerificationToken;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import com.practice.auth.infrastructure.persistence.postgresql.mapper.EmailVerificationTokenPersistenceMapper;
import com.practice.auth.infrastructure.persistence.postgresql.repository.IEmailVerificationTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EmailVerificationTokenPostgresqlAdapter implements IEmailVerificationTokenRepository {

    private final IEmailVerificationTokenJpaRepository jpaRepository;
    private final EmailVerificationTokenPersistenceMapper mapper;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(token)));
    }

    @Override
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<UUID> findUserIdsByExpiredBefore(LocalDateTime cutoff) {
        return jpaRepository.findUserIdsByExpiresAtBefore(cutoff);
    }

    @Override
    public void deleteAllExpiredBefore(LocalDateTime cutoff) {
        jpaRepository.deleteAllByExpiresAtBefore(cutoff);
    }
}
