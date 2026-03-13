package com.practice.user.infrastructure.persistence.postgresql.adapter;

import com.practice.user.domain.model.User;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import com.practice.user.infrastructure.persistence.postgresql.entity.UserJpaEntity;
import com.practice.user.infrastructure.persistence.postgresql.mapper.UserPersistenceMapper;
import com.practice.user.infrastructure.persistence.postgresql.repository.IUserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserPostgresqlAdapter implements IUserRepository {

    private final IUserJpaRepository userJpaRepository;
    private final UserPersistenceMapper mapper;

    @Override
    public User save(User user) {
        UserJpaEntity entity      = mapper.toJpaEntity(user);       // 1. convert → JPA
        UserJpaEntity savedEntity = userJpaRepository.save(entity); // 2. lưu vào DB
        return mapper.toDomain(savedEntity);                        // 3. convert → domain
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(EmailVO email) {
        return userJpaRepository.existsByEmail(email.getValue());
    }

    @Override
    public boolean existsByUsername(UsernameVO username) {
        return userJpaRepository.existsByUsername(username.getValue());
    }

    @Override
    public Optional<User> findByUsername(UsernameVO username) {
        return userJpaRepository.findByUsername(username.getValue()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(EmailVO email) {
        return userJpaRepository.findByEmail(email.getValue()).map(mapper::toDomain);
    }
}
