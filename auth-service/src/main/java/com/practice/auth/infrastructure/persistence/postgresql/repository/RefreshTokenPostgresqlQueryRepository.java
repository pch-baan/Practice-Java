package com.practice.auth.infrastructure.persistence.postgresql.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-specific queries for refresh token management.
 * Examples: bulk revocation with native SQL, token cleanup with date partitioning.
 *
 * Keeping this separate from IRefreshTokenJpaRepository which stays JPQL/Spring Data only.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenPostgresqlQueryRepository {

    private final EntityManager em;

    // Future: bulk cleanup expired tokens
    // public int deleteExpiredTokens() { ... }
}
