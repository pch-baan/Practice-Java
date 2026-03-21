package com.practice.user.infrastructure.persistence.postgresql.repository;

import com.practice.user.infrastructure.persistence.postgresql.entity.UserProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IUserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    Optional<UserProfileJpaEntity> findByUserId(UUID userId);
}
