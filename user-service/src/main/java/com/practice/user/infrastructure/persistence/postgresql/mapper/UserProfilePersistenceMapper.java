package com.practice.user.infrastructure.persistence.postgresql.mapper;

import com.practice.user.domain.model.UserProfile;
import com.practice.user.infrastructure.persistence.postgresql.entity.UserProfileJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UserProfilePersistenceMapper {

    public UserProfileJpaEntity toJpaEntity(UserProfile profile) {
        return UserProfileJpaEntity.builder()
            .id(profile.getId())
            .userId(profile.getUserId())
            .fullName(profile.getFullName())
            .displayName(profile.getDisplayName())
            .avatarUrl(profile.getAvatarUrl())
            .bio(profile.getBio())
            .phoneNumber(profile.getPhoneNumber())
            .dateOfBirth(profile.getDateOfBirth())
            .gender(profile.getGender())
            .locale(profile.getLocale())
            .timezone(profile.getTimezone())
            .createdAt(profile.getCreatedAt())
            .updatedAt(profile.getUpdatedAt())
            .build();
    }

    public UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstruct(
            entity.getId(),
            entity.getUserId(),
            entity.getFullName(),
            entity.getDisplayName(),
            entity.getAvatarUrl(),
            entity.getBio(),
            entity.getPhoneNumber(),
            entity.getDateOfBirth(),
            entity.getGender(),
            entity.getLocale(),
            entity.getTimezone(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
