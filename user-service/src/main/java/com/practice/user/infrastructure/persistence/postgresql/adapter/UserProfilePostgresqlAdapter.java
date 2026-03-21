package com.practice.user.infrastructure.persistence.postgresql.adapter;

import com.practice.user.domain.model.UserProfile;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.infrastructure.persistence.postgresql.mapper.UserProfilePersistenceMapper;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserProfileJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserProfilePostgresqlAdapter implements IUserProfileRepository {

    private final IUserProfileJpaRepository userProfileJpaRepository;
    private final UserProfilePersistenceMapper mapper;

    @Override
    public UserProfile save(UserProfile profile) {
        var entity      = mapper.toJpaEntity(profile);
        var savedEntity = userProfileJpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return userProfileJpaRepository.findByUserId(userId).map(mapper::toDomain);
    }
}
