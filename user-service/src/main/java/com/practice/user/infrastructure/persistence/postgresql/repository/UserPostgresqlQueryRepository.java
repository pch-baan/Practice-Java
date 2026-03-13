package com.practice.user.infrastructure.persistence.postgresql.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-specific queries that cannot be expressed with JPQL or Spring Data.
 * Examples: pg_trgm similarity search, JSONB operations, native aggregations.
 *
 * Keeping this separate from IUserJpaRepository which stays JPQL/Spring Data only.
 */
@Repository
@RequiredArgsConstructor
public class UserPostgresqlQueryRepository {

    private final EntityManager em;

    // Future: full-text search with pg_trgm
    // public List<UserJpaEntity> searchByUsernameOrEmail(String keyword) { ... }
}
