package com.practice.user.infrastructure.persistence.postgresql.repository;

import com.practice.user.domain.enums.UserStatusEnum;
import com.practice.user.infrastructure.persistence.postgresql.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<UserJpaEntity> findByUsername(String username);

    @Modifying
    @Query("DELETE FROM UserJpaEntity u WHERE u.id IN :ids AND u.status = :status")
    void deleteAllByIdInAndStatus(List<UUID> ids, UserStatusEnum status);
}
