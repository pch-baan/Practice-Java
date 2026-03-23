package com.practice.auth.infrastructure.persistence.postgresql.repository;

import com.practice.auth.infrastructure.persistence.postgresql.entity.EmailVerificationTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IEmailVerificationTokenJpaRepository extends JpaRepository<EmailVerificationTokenJpaEntity, UUID> {

    Optional<EmailVerificationTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM EmailVerificationTokenJpaEntity e WHERE e.expiresAt < :cutoff")
    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
